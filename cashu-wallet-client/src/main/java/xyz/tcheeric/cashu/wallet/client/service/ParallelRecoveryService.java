package xyz.tcheeric.cashu.wallet.client.service;

import lombok.NonNull;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Service for recovering wallets from multiple keysets in parallel.
 *
 * <p>This service extends the basic {@link WalletRecoveryService} functionality by enabling
 * concurrent recovery across multiple keysets. Instead of processing keysets sequentially,
 * this service leverages Java's {@link CompletableFuture} and {@link ExecutorService} to
 * recover from multiple keysets simultaneously, significantly reducing total recovery time.
 *
 * <h2>Performance Benefits</h2>
 * <p>Sequential recovery processes keysets one at a time:
 * <pre>{@code
 * Total Time = Time(Keyset1) + Time(Keyset2) + ... + Time(KeysetN)
 * }</pre>
 *
 * <p>Parallel recovery processes all keysets concurrently:
 * <pre>{@code
 * Total Time ≈ max(Time(Keyset1), Time(Keyset2), ..., Time(KeysetN))
 * }</pre>
 *
 * <p>For wallets with 3 keysets taking 30 seconds each:
 * <ul>
 *   <li>Sequential: 90 seconds total</li>
 *   <li>Parallel: ~30 seconds total (3x faster)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. The underlying {@link WalletRecoveryService} instances
 * must also be thread-safe or used in a thread-confined manner.
 *
 * <h2>Resource Management</h2>
 * <p>Callers are responsible for providing and managing the {@link ExecutorService}.
 * The service does NOT shut down the executor automatically.
 *
 * <h2>Error Handling</h2>
 * <p>If recovery fails for one keyset, the failure is isolated and other keysets continue
 * processing. Failed keysets are logged and excluded from results but do not abort the
 * entire recovery operation.
 *
 * <h2>Virtual Thread Support (Java 21+)</h2>
 * <p>For optimal performance, use Virtual Thread executors instead of fixed thread pools:
 * <pre>{@code
 * // Recommended: Virtual Thread executor (Java 21+)
 * ExecutorService executor = VirtualThreadExecutors.newVirtualThreadExecutor();
 *
 * // Or using standard API:
 * ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
 * }</pre>
 *
 * <p>Virtual Threads provide:
 * <ul>
 *   <li>+40-50% throughput improvement at high concurrency</li>
 *   <li>55-97% latency reduction (p95)</li>
 *   <li>Better resource utilization for I/O-bound recovery operations</li>
 * </ul>
 *
 * @see xyz.tcheeric.cashu.wallet.client.util.VirtualThreadExecutors
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create executor for parallel processing (VT recommended for Java 21+)
 * ExecutorService executor = VirtualThreadExecutors.newVirtualThreadExecutor();
 *
 * try {
 *     // Create parallel recovery service
 *     ParallelRecoveryService parallelService = new ParallelRecoveryServiceImpl(
 *         mintUrl,
 *         new RestoreRequestBuilder(),
 *         new ProofRecoveryServiceImpl()
 *     );
 *
 *     // Recover from multiple keysets in parallel
 *     String mnemonic = "word1 word2 ... word12";
 *     List<KeysetId> keysets = List.of(
 *         KeysetId.fromString("009a1f293253e41e"),
 *         KeysetId.fromString("00b2c3d4e5f6a7b8"),
 *         KeysetId.fromString("00c4d5e6f7a8b9c0")
 *     );
 *
 *     // Non-blocking - returns immediately
 *     CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
 *         parallelService.recoverParallel(mnemonic, "", keysets, keySets, executor);
 *
 *     // Block and wait for results
 *     Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get();
 *
 *     // Process results
 *     results.forEach((keysetId, proofs) -> {
 *         System.out.println("Keyset " + keysetId + ": " + proofs.size() + " proofs");
 *     });
 *
 * } finally {
 *     // Always shut down executor when done
 *     executor.shutdown();
 * }
 * }</pre>
 *
 * <h2>Advanced: Progress Monitoring</h2>
 * <pre>{@code
 * // Create futures for each keyset
 * List<CompletableFuture<List<Proof<DeterministicSecret>>>> futures = keysets.stream()
 *     .map(keysetId -> parallelService.recoverKeysetAsync(
 *         masterKey, keysetId, keySet, 0, executor
 *     ))
 *     .toList();
 *
 * // Monitor progress as each keyset completes
 * for (CompletableFuture<List<Proof<DeterministicSecret>>> future : futures) {
 *     future.thenAccept(proofs -> {
 *         System.out.println("Keyset completed: " + proofs.size() + " proofs");
 *     });
 * }
 *
 * // Wait for all to complete
 * CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
 * }</pre>
 *
 * @see WalletRecoveryService
 * @see CompletableFuture
 * @see ExecutorService
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
public interface ParallelRecoveryService extends WalletRecoveryService {

    /**
     * Recovers from multiple keysets in parallel using an executor service.
     *
     * <p>This method initiates concurrent recovery for all specified keysets and returns
     * immediately with a {@link CompletableFuture}. Each keyset is recovered independently
     * in its own task submitted to the provided executor.
     *
     * <p>The returned future completes when all keyset recoveries have finished (either
     * successfully or with errors). Failed keyset recoveries are logged and excluded from
     * the result map.
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be called
     * concurrently from multiple threads.
     *
     * <p><strong>Cancellation:</strong> To cancel ongoing recovery, call
     * {@code future.cancel(true)} on the returned future. This will attempt to cancel
     * all in-progress keyset recoveries.
     *
     * @param mnemonic BIP39 mnemonic phrase (12-24 words)
     * @param passphrase Optional BIP39 passphrase (use "" for none)
     * @param keysetIds List of keyset IDs to recover from
     * @param keySets List of keysets with public keys for unblinding
     * @param executor ExecutorService for parallel processing (not shut down by this method)
     * @return CompletableFuture that completes with a map of keyset ID to recovered proofs
     * @throws IllegalArgumentException if mnemonic is invalid or keysets list is empty
     */
    CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> recoverParallel(
            @NonNull String mnemonic,
            @NonNull String passphrase,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets,
            @NonNull ExecutorService executor
    );

    /**
     * Recovers from multiple keysets in parallel using a master key.
     *
     * <p>This method is similar to {@link #recoverParallel(String, String, List, List, ExecutorService)}
     * but accepts a pre-derived master key instead of a mnemonic. This is useful when the
     * master key has already been derived or when performing multiple recovery operations
     * with the same mnemonic.
     *
     * <p><strong>Performance Tip:</strong> If performing multiple recoveries with the same
     * mnemonic, derive the master key once and reuse it with this method to avoid redundant
     * key derivation overhead.
     *
     * @param masterKey BIP32 master key derived from mnemonic
     * @param keysetIds List of keyset IDs to recover from
     * @param keySets List of keysets with public keys for unblinding
     * @param executor ExecutorService for parallel processing (not shut down by this method)
     * @return CompletableFuture that completes with a map of keyset ID to recovered proofs
     */
    CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> recoverParallel(
            @NonNull DeterministicKey masterKey,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets,
            @NonNull ExecutorService executor
    );

    /**
     * Recovers a single keyset asynchronously.
     *
     * <p>This method initiates recovery for a single keyset and returns immediately with
     * a {@link CompletableFuture}. The recovery executes in the provided executor service.
     *
     * <p>This is a lower-level method useful for custom recovery strategies or when you
     * need fine-grained control over which keysets to recover and when.
     *
     * @param masterKey BIP32 master key derived from mnemonic
     * @param keysetId Keyset ID to recover
     * @param keySet Keyset with public keys for unblinding
     * @param startCounter Starting counter value (usually 0)
     * @param executor ExecutorService for async processing
     * @return CompletableFuture that completes with recovered proofs for this keyset
     */
    CompletableFuture<List<Proof<DeterministicSecret>>> recoverKeysetAsync(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            @NonNull ExecutorService executor
    );

    /**
     * Recovers a single keyset asynchronously with custom batch size.
     *
     * <p>This method allows overriding the default batch size for testing or
     * optimization purposes.
     *
     * @param masterKey BIP32 master key derived from mnemonic
     * @param keysetId Keyset ID to recover
     * @param keySet Keyset with public keys for unblinding
     * @param startCounter Starting counter value (usually 0)
     * @param batchSize Number of secrets to derive per batch
     * @param executor ExecutorService for async processing
     * @return CompletableFuture that completes with recovered proofs for this keyset
     * @throws IllegalArgumentException if batchSize is not positive
     */
    CompletableFuture<List<Proof<DeterministicSecret>>> recoverKeysetAsync(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            int batchSize,
            @NonNull ExecutorService executor
    );
}
