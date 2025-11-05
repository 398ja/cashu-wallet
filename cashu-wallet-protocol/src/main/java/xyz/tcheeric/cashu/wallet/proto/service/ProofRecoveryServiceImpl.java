package xyz.tcheeric.cashu.wallet.proto.service;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.java.Log;
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
@Log
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
            log.info("unblind_proofs_empty_response no_signatures_returned");
            return List.of();  // No signatures to unblind
        }

        log.info(String.format("unblind_proofs_started keyset=%s signatures_count=%d secrets_count=%d",
            keySet.getId(), blindSignatures.size(), secrets.size()));

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
                    log.warning(String.format(
                        "unblind_proof_invalid_blinding_factor index=%d counter=%d",
                        i, secret.getCounter()
                    ));
                    continue;  // Skip this proof
                }

                // Get the mint's public key for this amount
                PublicKey publicKey = keySet.getKeys().get(blindSig.getAmount());
                if (publicKey == null) {
                    log.warning(String.format(
                        "unblind_proof_missing_public_key index=%d amount=%d counter=%d",
                        i, blindSig.getAmount(), secret.getCounter()
                    ));
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

                log.fine(String.format(
                    "unblind_proof_success index=%d amount=%d counter=%d keyset=%s",
                    i, proof.getAmount(), secret.getCounter(), proof.getKeySetId()
                ));

            } catch (Exception e) {
                log.severe(String.format(
                    "unblind_proof_failed index=%d counter=%d error=%s",
                    i, secret.getCounter(), e.getMessage()
                ));
                // Continue processing other proofs - one failure shouldn't stop recovery
            }
        }

        log.info(String.format("unblind_proofs_completed keyset=%s recovered_count=%d",
            keySet.getId(), proofs.size()));

        return proofs;
    }

    @Override
    public List<Proof<DeterministicSecret>> filterUnspentProofs(
            @NonNull List<Proof<DeterministicSecret>> proofs,
            @NonNull String mintUrl) {

        // TODO: Implement NUT-07 spent-check integration
        // For now, return all proofs assuming they are unspent
        // This will be implemented in a future iteration

        log.warning("filter_unspent_proofs_not_implemented returning_all_proofs");

        return proofs;
    }
}
