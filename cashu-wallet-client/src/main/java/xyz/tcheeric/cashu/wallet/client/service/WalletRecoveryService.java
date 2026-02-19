package xyz.tcheeric.cashu.wallet.client.service;

import lombok.NonNull;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;

import java.util.List;

/**
 * Service for recovering wallets from BIP39 mnemonic phrases using NUT-13 deterministic derivation.
 *
 * <p>This service orchestrates the complete wallet recovery process:
 * <ol>
 *   <li>Derives BIP32 master key from mnemonic phrase</li>
 *   <li>For each keyset, derives batches of deterministic secrets and blinding factors</li>
 *   <li>Creates blinded messages and submits them to the mint via NUT-09 /restore endpoint</li>
 *   <li>Unblinds returned signatures to recover {@link Proof} objects</li>
 *   <li>Continues until 3 consecutive empty batches (no signatures returned)</li>
 * </ol>
 *
 * <p><strong>Recovery Algorithm:</strong>
 * <pre>{@code
 * for each keyset:
 *   counter = 0
 *   emptyBatches = 0
 *
 *   while emptyBatches < 3:
 *     1. Derive 100 secrets and blinding factors starting at counter
 *     2. Create blinded messages
 *     3. POST to /restore endpoint
 *     4. If signatures returned:
 *          - Reset emptyBatches to 0
 *          - Unblind signatures to create proofs
 *          - Add proofs to recovery result
 *        Else:
 *          - Increment emptyBatches
 *     5. counter += 100
 *
 *   return all recovered proofs
 * }</pre>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Create service with dependencies
 * WalletRecoveryService recoveryService = new WalletRecoveryServiceImpl(
 *     mintUrl,
 *     new RestoreRequestBuilder(),
 *     new ProofRecoveryServiceImpl()
 * );
 *
 * // Recover from mnemonic
 * String mnemonic = "word1 word2 ... word12";
 * List<KeysetId> keysets = List.of(new KeysetId("009a1f293253e41e"));
 * List<KeySet> keySets = ... // Fetch from mint
 *
 * List<Proof<DeterministicSecret>> recoveredProofs =
 *     recoveryService.recover(mnemonic, "", keysets, keySets);
 *
 * System.out.println("Recovered " + recoveredProofs.size() + " proofs");
 * }</pre>
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/09.md">NUT-09: Restore Signatures</a>
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
public interface WalletRecoveryService {

    /**
     * Default batch size for recovery requests (per NUT-13 specification).
     */
    int DEFAULT_BATCH_SIZE = 100;

    /**
     * Number of consecutive empty batches before stopping recovery for a keyset.
     */
    int MAX_EMPTY_BATCHES = 3;

    /**
     * Hard ceiling on counter value to prevent unbounded recovery loops.
     */
    int MAX_COUNTER = 100_000;

    /**
     * Maximum number of secrets to derive in a single batch.
     */
    int MAX_DERIVE_COUNT = 1_000;

    /**
     * Recovers wallet from mnemonic phrase.
     *
     * <p>This is the main entry point for wallet recovery. It derives the master key from
     * the mnemonic and passphrase, then recovers tokens for each specified keyset.
     *
     * @param mnemonic BIP39 mnemonic phrase (12-24 words)
     * @param passphrase Optional BIP39 passphrase (use "" for none)
     * @param keysetIds List of keyset IDs to recover from
     * @param keySets List of keysets with public keys for unblinding
     * @return List of all recovered proofs across all keysets
     * @throws IllegalArgumentException if mnemonic is invalid or keysets list is empty
     * @throws IllegalStateException if recovery fails
     */
    List<Proof<DeterministicSecret>> recover(
            @NonNull String mnemonic,
            @NonNull String passphrase,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets
    );

    /**
     * Recovers a single keyset with counter range.
     *
     * <p>This method handles the batched recovery for a specific keyset, implementing
     * the core recovery loop with 3-empty-batch termination.
     *
     * @param masterKey BIP32 master key derived from mnemonic
     * @param keysetId Keyset ID to recover
     * @param keySet Keyset with public keys for unblinding
     * @param startCounter Starting counter value (usually 0)
     * @return List of recovered proofs for this keyset
     * @throws IllegalStateException if recovery fails
     */
    List<Proof<DeterministicSecret>> recoverKeyset(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter
    );

    /**
     * Recovers a single keyset with custom batch size.
     *
     * <p>This method allows overriding the default batch size for testing or
     * optimization purposes.
     *
     * @param masterKey BIP32 master key derived from mnemonic
     * @param keysetId Keyset ID to recover
     * @param keySet Keyset with public keys for unblinding
     * @param startCounter Starting counter value (usually 0)
     * @param batchSize Number of secrets to derive per batch
     * @return List of recovered proofs for this keyset
     * @throws IllegalArgumentException if batchSize is not positive
     * @throws IllegalStateException if recovery fails
     */
    List<Proof<DeterministicSecret>> recoverKeyset(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            int batchSize
    );
}
