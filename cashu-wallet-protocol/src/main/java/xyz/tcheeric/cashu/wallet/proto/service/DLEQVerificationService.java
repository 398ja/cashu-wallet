package xyz.tcheeric.cashu.wallet.proto.service;

import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.nut12.DLEQProof;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;

/**
 * Service for verifying and enriching NUT-12 DLEQ proofs on wallet flows.
 *
 * <p>Verification covers two scenarios:
 * <ul>
 *   <li>Alice verifies blind signatures returned by the mint</li>
 *   <li>Carol verifies proofs received from another wallet</li>
 * </ul>
 *
 * <p>The returned {@link DLEQVerificationOutcome} distinguishes a cryptographically
 * verified subject from one that carried no proof at all. Under
 * {@link DLEQPolicy#REQUIRED} a missing proof is a failure, not a silent pass.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/12.md">NUT-12: DLEQ proofs</a>
 */
public interface DLEQVerificationService {

    /**
     * Returns the policy applied when a subject carries no DLEQ proof.
     *
     * @return the configured missing-proof policy
     */
    DLEQPolicy getPolicy();

    /**
     * Verifies the optional DLEQ proof attached to a blind signature (Alice path).
     *
     * @param blindSignature the blind signature that may contain a DLEQ proof
     * @param blindedMessage the blinded message that was signed (B')
     * @param mintPublicKey the mint's public key for the matching amount (A)
     * @return {@link DLEQVerificationOutcome#VERIFIED} or {@link DLEQVerificationOutcome#NO_PROOF_PRESENT}
     * @throws DLEQVerificationException when verification fails, or when a proof is
     *         absent while the policy requires one
     */
    DLEQVerificationOutcome verifyBlindSignature(
            BlindSignature blindSignature,
            PublicKey blindedMessage,
            PublicKey mintPublicKey
    );

    /**
     * Verifies the optional DLEQ proof attached to a proof (Carol path).
     *
     * @param proof the proof that may contain a DLEQ proof (including blinding factor r)
     * @param mintPublicKey the mint's public key for the matching amount (A)
     * @return {@link DLEQVerificationOutcome#VERIFIED} or {@link DLEQVerificationOutcome#NO_PROOF_PRESENT}
     * @throws DLEQVerificationException when verification fails, or when a proof is
     *         absent while the policy requires one
     */
    <T extends Secret> DLEQVerificationOutcome verifyProof(
            Proof<T> proof,
            PublicKey mintPublicKey
    );

    /**
     * Adds DLEQ data (e, s, r) to a proof so downstream wallets can verify it.
     *
     * @param proof the original proof without DLEQ metadata
     * @param blindingFactor the blinding factor r used when minting the proof
     * @param blindSignatureProof the DLEQ proof from the blind signature (e, s)
     * @return a new proof that carries the supplied DLEQ metadata
     * @throws IllegalArgumentException when the blinding factor is invalid
     */
    <T extends Secret> Proof<T> addDLEQToProof(
            Proof<T> proof,
            byte[] blindingFactor,
            DLEQProof blindSignatureProof
    );
}
