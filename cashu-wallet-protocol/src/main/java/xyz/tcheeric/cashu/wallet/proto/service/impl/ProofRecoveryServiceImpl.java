package xyz.tcheeric.cashu.wallet.proto.service.impl;

import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostCheckStateRequest;
import xyz.tcheeric.cashu.entities.rest.PostCheckStateResponse;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.proto.service.BDHKEUtilsService;
import xyz.tcheeric.cashu.wallet.proto.service.CheckStateClient;
import xyz.tcheeric.cashu.wallet.proto.service.DLEQVerificationException;
import xyz.tcheeric.cashu.wallet.proto.service.DLEQVerificationService;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;
import xyz.tcheeric.cashu.wallet.proto.tasks.UnblindSignatureTask;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link ProofRecoveryService}.
 *
 * <p>This implementation uses the {@link UnblindSignatureTask} to unblind each signature
 * returned by the mint during wallet recovery. It properly handles the matching between
 * blind signatures and their corresponding secrets/blinding factors.
 *
 * @see ProofRecoveryService
 * @see UnblindSignatureTask
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
@Nut(9)
@Slf4j
@ToString
public class ProofRecoveryServiceImpl implements ProofRecoveryService {

    /**
     * BDHKE utilities service for signature unblinding.
     */
    private final BDHKEUtilsService bdkhUtils;

    /**
     * Service for attaching and verifying optional DLEQ proofs.
     */
    private final DLEQVerificationService dleqVerificationService;

    /**
     * Client for invoking the mint's /checkstate endpoint.
     */
    private final CheckStateClient checkStateClient;

    /**
     * Creates a ProofRecoveryServiceImpl with default dependencies.
     */
    public ProofRecoveryServiceImpl() {
        this(new BDHKEUtilsServiceImpl(), new DefaultDLEQVerificationService(), new DefaultCheckStateClient());
    }

    /**
     * Creates a ProofRecoveryServiceImpl with a custom BDHKE utility.
     */
    public ProofRecoveryServiceImpl(BDHKEUtilsService bdkhUtils) {
        this(bdkhUtils, new DefaultDLEQVerificationService(), new DefaultCheckStateClient());
    }

    public ProofRecoveryServiceImpl(
            BDHKEUtilsService bdkhUtils,
            DLEQVerificationService dleqVerificationService
    ) {
        this(bdkhUtils, dleqVerificationService, new DefaultCheckStateClient());
    }

    public ProofRecoveryServiceImpl(
            BDHKEUtilsService bdkhUtils,
            DLEQVerificationService dleqVerificationService,
            CheckStateClient checkStateClient
    ) {
        this.bdkhUtils = Objects.requireNonNull(bdkhUtils, "BDHKE utils service cannot be null");
        this.dleqVerificationService = Objects.requireNonNull(dleqVerificationService, "DLEQ verification service cannot be null");
        this.checkStateClient = Objects.requireNonNull(checkStateClient, "CheckState client cannot be null");
    }

    @Override
    public List<Proof<DeterministicSecret>> unblindAndCreateProofs(
            @NonNull PostRestoreResponse response,
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors,
            @NonNull KeySet keySet) {

        // Validate inputs
        if (secrets.size() != blindingFactors.size()) {
            throw new IllegalArgumentException(String.format(
                "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                secrets.size(), blindingFactors.size()
            ));
        }

        List<BlindSignature> blindSignatures = response.getBlindSignatures();
        if (blindSignatures == null || blindSignatures.isEmpty()) {
            log.info("proof_recovery response_empty keyset={} action=skip_unblinding", keySet.getId());
            return List.of();  // No signatures to unblind
        }

        log.info("proof_recovery unblinding_started keyset={} signatures_count={} secrets_count={}",
            keySet.getId(), blindSignatures.size(), secrets.size());

        List<Proof<DeterministicSecret>> proofs = new ArrayList<>(blindSignatures.size());

        // The restore response returns blind signatures in the same order as the blinded messages sent
        // We need to match each signature with its corresponding secret and blinding factor
        for (int i = 0; i < blindSignatures.size() && i < secrets.size(); i++) {
            BlindSignature blindSig = blindSignatures.get(i);
            DeterministicSecret secret = secrets.get(i);
            byte[] blindingFactor = blindingFactors.get(i);

            try {
                // Validate blinding factor
                if (blindingFactor == null || blindingFactor.length != 32) {
                    log.warn("proof_recovery blinding_factor_invalid index={} counter={} action=skip_signature",
                        i, secret.getCounter());
                    continue;  // Skip this proof
                }

                // Get the mint's public key for this amount
                PublicKey publicKey = keySet.getKeys().get(blindSig.getAmount());
                if (publicKey == null) {
                    log.warn("proof_recovery public_key_missing index={} amount={} counter={} action=skip_signature",
                        i, blindSig.getAmount(), secret.getCounter());
                    continue;  // Skip this proof
                }

                // Convert blinding factor to BigInteger
                BigInteger r = Utils.bigIntFromBytes(blindingFactor);

                if (blindSig.hasDLEQProof()) {
                    byte[] blindedBytes = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);
                    PublicKey blindedMessage = PublicKey.fromBytes(blindedBytes);
                    dleqVerificationService.verifyBlindSignature(blindSig, blindedMessage, publicKey);
                }

                // Unblind the signature using UnblindSignatureTask
                UnblindSignatureTask<DeterministicSecret> unblindTask =
                    new UnblindSignatureTask<>(
                        blindSig,
                        r,
                        publicKey,
                        secret,
                        bdkhUtils
                    );

                Proof<DeterministicSecret> proof = unblindTask.execute();
                proof = dleqVerificationService.addDLEQToProof(
                    proof,
                    blindingFactor,
                    blindSig.getDleq()
                );

                if (proof.hasDLEQProof()) {
                    dleqVerificationService.verifyProof(proof, publicKey);
                }

                proofs.add(proof);

                log.debug("proof_recovery unblinding_success index={} amount={} counter={} keyset={}",
                    i, proof.getAmount(), secret.getCounter(), proof.getKeySetId());

            } catch (DLEQVerificationException e) {
                log.error("proof_recovery dleq_verification_failed index={} counter={} error={} action=skip_signature",
                    i, secret.getCounter(), e.getMessage(), e);
            } catch (Exception e) {
                log.error("proof_recovery unblinding_failed index={} counter={} error={} impact=continuing_batch",
                    i, secret.getCounter(), e.getMessage(), e);
                // Continue processing other proofs - one failure shouldn't stop recovery
            }
        }

        log.info("proof_recovery unblinding_completed keyset={} recovered_count={}",
            keySet.getId(), proofs.size());

        return proofs;
    }

    @Override
    public List<Proof<DeterministicSecret>> filterUnspentProofs(
            @NonNull List<Proof<DeterministicSecret>> proofs,
            @NonNull String mintUrl) {

        if (proofs.isEmpty()) {
            log.info("proof_recovery spent_check_skipped reason=no_proofs mint_url={}", mintUrl);
            return List.of();
        }

        List<HashToCurveSecret> hashedSecrets = proofs.stream()
            .map(HashToCurveSecret::new)
            .toList();

        PostCheckStateRequest request = new PostCheckStateRequest(hashedSecrets);
        PostCheckStateResponse response = checkStateClient.checkState(mintUrl, request);

        Map<String, String> stateBySecret = new HashMap<>();
        Optional.ofNullable(response.getStates()).orElse(List.of()).forEach(state -> {
            if (state.getHashToCurveSecret() != null && state.getState() != null) {
                stateBySecret.put(state.getHashToCurveSecret().toString(), state.getState());
            }
        });

        if (stateBySecret.isEmpty()) {
            log.warn("proof_recovery spent_check_no_states mint_url={} action=return_all", mintUrl);
            return proofs;
        }

        List<Proof<DeterministicSecret>> unspent = new ArrayList<>();
        for (Proof<DeterministicSecret> proof : proofs) {
            String secretKey = new HashToCurveSecret(proof).toString();
            String state = stateBySecret.get(secretKey);
            if (state == null) {
                log.warn("proof_recovery spent_check_state_missing keyset={} amount={} action=drop",
                    proof.getKeySetId(), proof.getAmount());
                unspent.add(proof);
                continue;
            }

            if (isUnspent(state)) {
                unspent.add(proof);
            } else {
                log.info("proof_recovery proof_marked_spent keyset={} amount={} state={}",
                    proof.getKeySetId(), proof.getAmount(), state);
            }
        }

        log.info("proof_recovery spent_check_completed mint_url={} input_count={} unspent_count={}",
            mintUrl, proofs.size(), unspent.size());

        return unspent;
    }

    private boolean isUnspent(String state) {
        String normalized = state.trim().toLowerCase();
        return "unspent".equals(normalized) || "0".equals(normalized);
    }
}
