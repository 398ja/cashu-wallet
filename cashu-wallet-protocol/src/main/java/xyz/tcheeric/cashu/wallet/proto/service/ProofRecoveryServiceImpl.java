package xyz.tcheeric.cashu.wallet.proto.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.proto.tasks.UnblindSignatureTask;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

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
@AllArgsConstructor
@ToString
public class ProofRecoveryServiceImpl implements ProofRecoveryService {

    /**
     * BDHKE utilities service for signature unblinding.
     */
    private final BDHKEUtilsService bdkhUtils;

    /**
     * Creates a ProofRecoveryServiceImpl with default BDHKE utilities.
     */
    public ProofRecoveryServiceImpl() {
        this(new BDHKEUtilsServiceImpl());
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
                proofs.add(proof);

                log.debug("proof_recovery unblinding_success index={} amount={} counter={} keyset={}",
                    i, proof.getAmount(), secret.getCounter(), proof.getKeySetId());

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

        // TODO: Implement NUT-07 spent-check integration
        // For now, return all proofs assuming they are unspent
        // This will be implemented in a future iteration

        log.warn("proof_recovery spent_check_unimplemented mint_url={} action=return_all", mintUrl);

        return proofs;
    }
}
