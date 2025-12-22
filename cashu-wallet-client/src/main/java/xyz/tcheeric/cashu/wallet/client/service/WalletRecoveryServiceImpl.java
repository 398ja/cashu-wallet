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
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestRestore;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;
import xyz.tcheeric.cashu.wallet.proto.service.impl.ProofRecoveryServiceImpl;
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
@Slf4j
@ToString(exclude = {"mintUrl", "restoreClientFactory"}) // Exclude URL and factory from logs for cleaner output
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
     * Factory for creating restore clients. Allows tests to provide stubs.
     */
    private final RestoreClientFactory restoreClientFactory;

    /**
     * Creates a WalletRecoveryServiceImpl with default dependencies.
     *
     * @param mintUrl Mint base URL
     */
    public WalletRecoveryServiceImpl(@NonNull String mintUrl) {
        this(mintUrl, new RestoreRequestBuilder(), new ProofRecoveryServiceImpl(), new DefaultRestoreClientFactory());
    }

    public WalletRecoveryServiceImpl(
            @NonNull String mintUrl,
            @NonNull RestoreRequestBuilder requestBuilder,
            @NonNull ProofRecoveryService proofRecoveryService) {
        this(mintUrl, requestBuilder, proofRecoveryService, new DefaultRestoreClientFactory());
    }

    public WalletRecoveryServiceImpl(
            @NonNull String mintUrl,
            @NonNull RestoreRequestBuilder requestBuilder,
            @NonNull ProofRecoveryService proofRecoveryService,
            @NonNull RestoreClientFactory restoreClientFactory) {
        this.mintUrl = mintUrl;
        this.requestBuilder = requestBuilder;
        this.proofRecoveryService = proofRecoveryService;
        this.restoreClientFactory = restoreClientFactory;
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

        log.info("wallet_recovery service_started keysets_count={} mint_url={}",
            keysetIds.size(), mintUrl);

        // Derive master key from mnemonic
        DeterministicKey masterKey;
        try {
            masterKey = Bip39.mnemonicToMasterKey(mnemonic, passphrase);
        } catch (Exception e) {
            log.error("wallet_recovery master_key_derivation_failed error={} impact=abort", e.getMessage(), e);
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
                    log.warn("wallet_recovery keyset_missing keyset={} action=skip", keysetId);
                    continue;
                }

                log.info("wallet_recovery keyset_processing_started keyset={}", keysetId);

                List<Proof<DeterministicSecret>> keysetProofs =
                    recoverKeyset(masterKey, keysetId, keySet, 0);

                allProofs.addAll(keysetProofs);

                log.info("wallet_recovery keyset_processing_completed keyset={} recovered_count={}",
                    keysetId, keysetProofs.size());

            } catch (Exception e) {
                log.error("wallet_recovery keyset_processing_failed keyset={} error={} impact=continuing_other_keysets",
                    keysetId, e.getMessage(), e);
                // Continue with other keysets even if one fails
            }
        }

        log.info("wallet_recovery service_completed total_proofs={} requested_keysets={}",
            allProofs.size(), keysetIds.size());

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

        log.info("wallet_recovery keyset_recovery_started keyset={} start_counter={} batch_size={}",
            keysetId, startCounter, batchSize);

        List<Proof<DeterministicSecret>> allProofs = new ArrayList<>();
        int counter = startCounter;
        int emptyBatches = 0;
        int batchNumber = 0;

        while (emptyBatches < MAX_EMPTY_BATCHES) {
            batchNumber++;

            log.debug("wallet_recovery batch_processing_started keyset={} batch={} counter={} empty_batches={}",
                keysetId, batchNumber, counter, emptyBatches);

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
                RequestRestore restoreClient = restoreClientFactory.create(mintUrl, keySet, request);
                PostRestoreResponse response = restoreClient.execute();

                // Step 4: Process response
                if (response == null || response.getBlindSignatures() == null ||
                    response.getBlindSignatures().isEmpty()) {

                    emptyBatches++;
                    log.debug("wallet_recovery batch_empty keyset={} batch={} counter={} empty_batch_count={}",
                        keysetId, batchNumber, counter, emptyBatches);

                } else {
                    // Signatures found - reset empty batch counter
                    emptyBatches = 0;

                    log.info("wallet_recovery signatures_detected keyset={} batch={} signatures={}",
                        keysetId, batchNumber, response.getBlindSignatures().size());

                    // Step 5: Unblind signatures to create proofs
                    List<Proof<DeterministicSecret>> batchProofs =
                        proofRecoveryService.unblindAndCreateProofs(
                            response,
                            deriveResult.getSecrets(),
                            deriveResult.getBlindingFactors(),
                            keySet
                        );

                    allProofs.addAll(batchProofs);

                    log.info("wallet_recovery batch_processing_completed keyset={} batch={} proofs_created={} total_accumulated={}",
                        keysetId, batchNumber, batchProofs.size(), allProofs.size());
                }

            } catch (Exception e) {
                log.error("wallet_recovery batch_processing_failed keyset={} batch={} counter={} error={} impact=increment_empty_batches",
                    keysetId, batchNumber, counter, e.getMessage(), e);

                // Increment empty batches on error to eventually terminate
                emptyBatches++;

                // If we hit too many errors, stop recovery for this keyset
                if (emptyBatches >= MAX_EMPTY_BATCHES) {
                    log.warn("wallet_recovery batch_error_limit_reached keyset={} action=stop_recovery", keysetId);
                    break;
                }
            }

            // Move to next batch
            counter += batchSize;
        }

        log.info("wallet_recovery keyset_recovery_finished keyset={} total_proofs={} batches_processed={}",
            keysetId, allProofs.size(), batchNumber);

        return allProofs;
    }

    private static final class DefaultRestoreClientFactory implements RestoreClientFactory {

        @Override
        public RequestRestore create(String mintUrl, KeySet keySet, PostRestoreRequest request) {
            return new RequestRestore(mintUrl, request);
        }
    }
}
