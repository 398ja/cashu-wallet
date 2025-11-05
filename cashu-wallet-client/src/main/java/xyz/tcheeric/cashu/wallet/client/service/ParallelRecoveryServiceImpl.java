package xyz.tcheeric.cashu.wallet.client.service;

import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ParallelRecoveryService}.
 *
 * <p>This implementation extends {@link WalletRecoveryServiceImpl} to provide parallel
 * recovery across multiple keysets using Java's {@link CompletableFuture} API.
 *
 * <h2>Implementation Strategy</h2>
 * <p>The service creates one {@link CompletableFuture} per keyset, each running the
 * standard sequential recovery logic from {@link WalletRecoveryServiceImpl}. All futures
 * are submitted to the provided {@link ExecutorService} and combined using
 * {@link CompletableFuture#allOf(CompletableFuture[])}.
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for result collection</li>
 *   <li>Each keyset recovery is independent (no shared mutable state)</li>
 *   <li>Logging is thread-safe via SLF4J</li>
 * </ul>
 *
 * <h2>Error Isolation</h2>
 * <p>If one keyset recovery fails, it does not affect other keysets. Failed recoveries
 * are logged at ERROR level and excluded from the result map.
 *
 * <h2>Resource Usage</h2>
 * <p>Each keyset recovery creates:
 * <ul>
 *   <li>Multiple HTTP connections to the mint (one per batch)</li>
 *   <li>Cryptographic operations (secret derivation, unblinding)</li>
 *   <li>Memory for secrets, blinding factors, and proofs</li>
 * </ul>
 *
 * <p>Recommended thread pool size: 2-4 threads for typical use cases.
 * More threads may help with many keysets but increases mint server load.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create service
 * ParallelRecoveryService service = new ParallelRecoveryServiceImpl(mintUrl);
 *
 * // Create fixed thread pool
 * ExecutorService executor = Executors.newFixedThreadPool(3);
 *
 * try {
 *     // Start parallel recovery
 *     CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
 *         service.recoverParallel(mnemonic, "", keysetIds, keySets, executor);
 *
 *     // Wait for completion with timeout
 *     Map<KeysetId, List<Proof<DeterministicSecret>>> results =
 *         future.get(5, TimeUnit.MINUTES);
 *
 *     // Process results
 *     int totalProofs = results.values().stream()
 *         .mapToInt(List::size)
 *         .sum();
 *
 *     System.out.println("Recovered " + totalProofs + " proofs from " +
 *                        results.size() + " keysets");
 *
 * } catch (TimeoutException e) {
 *     System.err.println("Recovery timed out");
 *     future.cancel(true);
 * } finally {
 *     executor.shutdown();
 * }
 * }</pre>
 *
 * @see ParallelRecoveryService
 * @see WalletRecoveryServiceImpl
 * @see CompletableFuture
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@Nut(13)
@Slf4j
@ToString(callSuper = true)
public class ParallelRecoveryServiceImpl extends WalletRecoveryServiceImpl implements ParallelRecoveryService {

    /**
     * Creates a ParallelRecoveryServiceImpl with default dependencies.
     *
     * @param mintUrl Mint base URL for restore requests
     */
    public ParallelRecoveryServiceImpl(@NonNull String mintUrl) {
        super(mintUrl);
        log.debug("parallel_recovery service_created mint_url={}", mintUrl);
    }

    /**
     * Creates a ParallelRecoveryServiceImpl with custom dependencies.
     *
     * @param mintUrl Mint base URL
     * @param requestBuilder Custom restore request builder
     * @param proofRecoveryService Custom proof recovery service
     */
    public ParallelRecoveryServiceImpl(
            @NonNull String mintUrl,
            @NonNull RestoreRequestBuilder requestBuilder,
            @NonNull ProofRecoveryService proofRecoveryService) {
        super(mintUrl, requestBuilder, proofRecoveryService);
        log.debug("parallel_recovery service_created mint_url={} custom_dependencies=true", mintUrl);
    }

    /**
     * Creates a ParallelRecoveryServiceImpl with full custom dependencies.
     *
     * @param mintUrl Mint base URL
     * @param requestBuilder Custom restore request builder
     * @param proofRecoveryService Custom proof recovery service
     * @param restoreClientFactory Custom restore client factory
     */
    public ParallelRecoveryServiceImpl(
            @NonNull String mintUrl,
            @NonNull RestoreRequestBuilder requestBuilder,
            @NonNull ProofRecoveryService proofRecoveryService,
            @NonNull RestoreClientFactory restoreClientFactory) {
        super(mintUrl, requestBuilder, proofRecoveryService, restoreClientFactory);
        log.debug("parallel_recovery service_created mint_url={} custom_factory=true", mintUrl);
    }

    @Override
    public CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> recoverParallel(
            @NonNull String mnemonic,
            @NonNull String passphrase,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets,
            @NonNull ExecutorService executor) {

        // Validate inputs
        if (keysetIds.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Keyset IDs list cannot be empty")
            );
        }

        if (keySets.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("KeySets list cannot be empty")
            );
        }

        if (!Bip39.isValidMnemonic(mnemonic)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid BIP39 mnemonic phrase")
            );
        }

        log.info("parallel_recovery service_started keysets_count={} executor={}",
            keysetIds.size(), executor.getClass().getSimpleName());

        // Derive master key
        CompletableFuture<DeterministicKey> masterKeyFuture = CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("parallel_recovery master_key_derivation_started");
                DeterministicKey key = Bip39.mnemonicToMasterKey(mnemonic, passphrase);
                log.debug("parallel_recovery master_key_derivation_completed");
                return key;
            } catch (Exception e) {
                log.error("parallel_recovery master_key_derivation_failed error={}", e.getMessage(), e);
                throw new IllegalStateException("Failed to derive master key: " + e.getMessage(), e);
            }
        }, executor);

        // Once master key is derived, start parallel recovery
        return masterKeyFuture.thenCompose(masterKey ->
            recoverParallel(masterKey, keysetIds, keySets, executor)
        );
    }

    @Override
    public CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> recoverParallel(
            @NonNull DeterministicKey masterKey,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets,
            @NonNull ExecutorService executor) {

        log.info("parallel_recovery keyset_recovery_started keysets_count={}", keysetIds.size());

        // Create a map for quick KeySet lookup by ID
        Map<String, KeySet> keySetMap = keySets.stream()
            .collect(Collectors.toMap(KeySet::getId, ks -> ks));

        // Create a future for each keyset recovery
        List<CompletableFuture<KeysetRecoveryResult>> futures = new ArrayList<>(keysetIds.size());

        for (KeysetId keysetId : keysetIds) {
            // Find corresponding KeySet
            KeySet keySet = keySetMap.get(keysetId.toString());
            if (keySet == null) {
                log.warn("parallel_recovery keyset_missing keyset={} action=skip", keysetId);
                // Create a completed future with empty result for missing keyset
                futures.add(CompletableFuture.completedFuture(
                    new KeysetRecoveryResult(keysetId, List.of(), false)
                ));
                continue;
            }

            // Create async recovery task for this keyset
            CompletableFuture<KeysetRecoveryResult> future =
                recoverKeysetAsync(masterKey, keysetId, keySet, 0, executor)
                    .thenApply(proofs -> {
                        log.info("parallel_recovery keyset_completed keyset={} proofs_count={}",
                            keysetId, proofs.size());
                        return new KeysetRecoveryResult(keysetId, proofs, true);
                    })
                    .exceptionally(error -> {
                        log.error("parallel_recovery keyset_failed keyset={} error={}",
                            keysetId, error.getMessage(), error);
                        // Return empty result on error but mark as failed
                        return new KeysetRecoveryResult(keysetId, List.of(), false);
                    });

            futures.add(future);
        }

        // Combine all futures and collect results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                // Collect all results into a map
                Map<KeysetId, List<Proof<DeterministicSecret>>> results = new ConcurrentHashMap<>();

                for (CompletableFuture<KeysetRecoveryResult> future : futures) {
                    try {
                        KeysetRecoveryResult result = future.join();
                        if (result.success()) {
                            results.put(result.keysetId(), result.proofs());
                        }
                    } catch (Exception e) {
                        // Should not happen since we handled exceptions in exceptionally()
                        log.error("parallel_recovery result_collection_error error={}", e.getMessage(), e);
                    }
                }

                int totalProofs = results.values().stream()
                    .mapToInt(List::size)
                    .sum();

                log.info("parallel_recovery service_completed successful_keysets={} total_proofs={}",
                    results.size(), totalProofs);

                return results;
            });
    }

    @Override
    public CompletableFuture<List<Proof<DeterministicSecret>>> recoverKeysetAsync(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            @NonNull ExecutorService executor) {

        return recoverKeysetAsync(masterKey, keysetId, keySet, startCounter, DEFAULT_BATCH_SIZE, executor);
    }

    @Override
    public CompletableFuture<List<Proof<DeterministicSecret>>> recoverKeysetAsync(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            int batchSize,
            @NonNull ExecutorService executor) {

        log.debug("parallel_recovery keyset_async_started keyset={} start_counter={} batch_size={}",
            keysetId, startCounter, batchSize);

        // Execute the synchronous recoverKeyset method asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                return recoverKeyset(masterKey, keysetId, keySet, startCounter, batchSize);
            } catch (Exception e) {
                log.error("parallel_recovery keyset_async_failed keyset={} error={}",
                    keysetId, e.getMessage(), e);
                throw e;
            }
        }, executor);
    }

    /**
     * Internal record for tracking keyset recovery results.
     *
     * @param keysetId Keyset that was recovered
     * @param proofs List of recovered proofs (empty if failed)
     * @param success Whether recovery succeeded
     */
    private record KeysetRecoveryResult(
        KeysetId keysetId,
        List<Proof<DeterministicSecret>> proofs,
        boolean success
    ) {}
}
