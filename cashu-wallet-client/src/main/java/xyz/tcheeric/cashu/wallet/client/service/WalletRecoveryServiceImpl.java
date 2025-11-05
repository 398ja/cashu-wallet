package xyz.tcheeric.cashu.wallet.client.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.java.Log;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestRestore;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;
import xyz.tcheeric.cashu.wallet.proto.tasks.DeriveSecretsTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link WalletRecoveryService}.
 *
 * <p>This implementation coordinates all the components needed for NUT-13 wallet recovery:
 * <ul>
 *   <li>{@link DeriveSecretsTask} - Derives deterministic secrets and blinding factors</li>
 *   <li>{@link RestoreRequestBuilder} - Creates blinded messages for restore requests</li>
 *   <li>{@link RequestRestore} - Submits restore requests to the mint</li>
 *   <li>{@link ProofRecoveryService} - Unblinds signatures to create proofs</li>
 * </ul>
 *
 * <p>The recovery process uses a batched approach with 3-empty-batch termination to
 * efficiently recover tokens while minimizing unnecessary network requests.
 *
 * @see WalletRecoveryService
 * @see DeriveSecretsTask
 * @see RestoreRequestBuilder
 * @see ProofRecoveryService
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
@Nut(13)
@Log
@AllArgsConstructor
@ToString(exclude = "mintUrl") // Exclude URL from logs for cleaner output
public class WalletRecoveryServiceImpl implements WalletRecoveryService {

    /**
     * Mint base URL for restore requests.
     */
    private final String mintUrl;

    /**
     * Builder for creating restore requests.
     */
    private final RestoreRequestBuilder requestBuilder;

    /**
     * Service for unblinding signatures and creating proofs.
     */
    private final ProofRecoveryService proofRecoveryService;

    /**
     * Creates a WalletRecoveryServiceImpl with default dependencies.
     *
     * @param mintUrl Mint base URL
     */
    public WalletRecoveryServiceImpl(@NonNull String mintUrl) {
        this(mintUrl, new RestoreRequestBuilder(), new ProofRecoveryServiceImpl());
    }

    @Override
    public List<Proof<DeterministicSecret>> recover(
            @NonNull String mnemonic,
            @NonNull String passphrase,
            @NonNull List<KeysetId> keysetIds,
            @NonNull List<KeySet> keySets) {

        // Validate inputs
        if (keysetIds.isEmpty()) {
            throw new IllegalArgumentException("Keyset IDs list cannot be empty");
        }

        if (keySets.isEmpty()) {
            throw new IllegalArgumentException("KeySets list cannot be empty");
        }

        // Validate mnemonic
        if (!Bip39.isValidMnemonic(mnemonic)) {
            throw new IllegalArgumentException("Invalid BIP39 mnemonic phrase");
        }

        log.info(String.format("wallet_recovery_started keysets_count=%d mint=%s",
            keysetIds.size(), mintUrl));

        // Derive master key from mnemonic
        DeterministicKey masterKey;
        try {
            masterKey = Bip39.mnemonicToMasterKey(mnemonic, passphrase);
        } catch (Exception e) {
            log.severe(String.format("master_key_derivation_failed error=%s", e.getMessage()));
            throw new IllegalStateException("Failed to derive master key from mnemonic: " + e.getMessage(), e);
        }

        // Create a map for quick KeySet lookup by ID
        Map<String, KeySet> keySetMap = keySets.stream()
            .collect(Collectors.toMap(KeySet::getId, ks -> ks));

        // Recover proofs for each keyset
        List<Proof<DeterministicSecret>> allProofs = new ArrayList<>();
        for (KeysetId keysetId : keysetIds) {
            try {
                // Find the corresponding KeySet
                KeySet keySet = keySetMap.get(keysetId.toString());
                if (keySet == null) {
                    log.warning(String.format("keyset_not_found keyset=%s skipping", keysetId));
                    continue;
                }

                log.info(String.format("recovering_keyset keyset=%s", keysetId));

                List<Proof<DeterministicSecret>> keysetProofs =
                    recoverKeyset(masterKey, keysetId, keySet, 0);

                allProofs.addAll(keysetProofs);

                log.info(String.format("keyset_recovery_completed keyset=%s recovered_count=%d",
                    keysetId, keysetProofs.size()));

            } catch (Exception e) {
                log.severe(String.format("keyset_recovery_failed keyset=%s error=%s",
                    keysetId, e.getMessage()));
                // Continue with other keysets even if one fails
            }
        }

        log.info(String.format("wallet_recovery_completed total_proofs=%d keysets_recovered=%d",
            allProofs.size(), keysetIds.size()));

        return allProofs;
    }

    @Override
    public List<Proof<DeterministicSecret>> recoverKeyset(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter) {

        return recoverKeyset(masterKey, keysetId, keySet, startCounter, DEFAULT_BATCH_SIZE);
    }

    @Override
    public List<Proof<DeterministicSecret>> recoverKeyset(
            @NonNull DeterministicKey masterKey,
            @NonNull KeysetId keysetId,
            @NonNull KeySet keySet,
            int startCounter,
            int batchSize) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive, got: " + batchSize);
        }

        if (startCounter < 0) {
            throw new IllegalArgumentException("Start counter must be non-negative, got: " + startCounter);
        }

        log.info(String.format("keyset_recovery_started keyset=%s start_counter=%d batch_size=%d",
            keysetId, startCounter, batchSize));

        List<Proof<DeterministicSecret>> allProofs = new ArrayList<>();
        int counter = startCounter;
        int emptyBatches = 0;
        int batchNumber = 0;

        while (emptyBatches < MAX_EMPTY_BATCHES) {
            batchNumber++;

            log.fine(String.format("processing_batch keyset=%s batch=%d counter=%d empty_batches=%d",
                keysetId, batchNumber, counter, emptyBatches));

            try {
                // Step 1: Derive secrets and blinding factors for this batch
                DeriveSecretsTask deriveTask = new DeriveSecretsTask(
                    masterKey,
                    keysetId,
                    counter,
                    batchSize
                );
                DeriveSecretsTask.DeriveSecretsResult deriveResult = deriveTask.execute();

                // Step 2: Create blinded messages
                // Note: We use amount=1 as a default. In a real implementation, you might want
                // to try multiple amounts or get the amount from somewhere else.
                PostRestoreRequest request = requestBuilder.buildRequestFromSecrets(
                    deriveResult.getSecrets(),
                    deriveResult.getBlindingFactors(),
                    1  // TODO: Support multiple amounts or get from configuration
                );

                // Step 3: Submit restore request to mint
                RequestRestore restoreClient = new RequestRestore(mintUrl, request);
                PostRestoreResponse response = restoreClient.execute();

                // Step 4: Process response
                if (response == null || response.getBlindSignatures() == null ||
                    response.getBlindSignatures().isEmpty()) {

                    emptyBatches++;
                    log.fine(String.format("empty_batch keyset=%s batch=%d counter=%d empty_count=%d",
                        keysetId, batchNumber, counter, emptyBatches));

                } else {
                    // Signatures found - reset empty batch counter
                    emptyBatches = 0;

                    log.info(String.format("signatures_found keyset=%s batch=%d signatures=%d",
                        keysetId, batchNumber, response.getBlindSignatures().size()));

                    // Step 5: Unblind signatures to create proofs
                    List<Proof<DeterministicSecret>> batchProofs =
                        proofRecoveryService.unblindAndCreateProofs(
                            response,
                            deriveResult.getSecrets(),
                            deriveResult.getBlindingFactors(),
                            keySet
                        );

                    allProofs.addAll(batchProofs);

                    log.info(String.format("batch_recovered keyset=%s batch=%d proofs=%d total=%d",
                        keysetId, batchNumber, batchProofs.size(), allProofs.size()));
                }

            } catch (Exception e) {
                log.severe(String.format("batch_processing_failed keyset=%s batch=%d counter=%d error=%s",
                    keysetId, batchNumber, counter, e.getMessage()));

                // Increment empty batches on error to eventually terminate
                emptyBatches++;

                // If we hit too many errors, stop recovery for this keyset
                if (emptyBatches >= MAX_EMPTY_BATCHES) {
                    log.warning(String.format("max_errors_reached keyset=%s stopping_recovery", keysetId));
                    break;
                }
            }

            // Move to next batch
            counter += batchSize;
        }

        log.info(String.format("keyset_recovery_finished keyset=%s total_proofs=%d batches_processed=%d",
            keysetId, allProofs.size(), batchNumber));

        return allProofs;
    }
}
