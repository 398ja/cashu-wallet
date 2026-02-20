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
 */
public interface DLEQVerificationService {

    /**
     * Verifies the optional DLEQ proof attached to a blind signature (Alice path).
     *
     * @param blindSignature the blind signature that may contain a DLEQ proof
     * @param blindedMessage the blinded message that was signed (B')
     * @param mintPublicKey the mint's public key for the matching amount (A)
     * @return true when either no DLEQ proof is present or verification succeeds
     * @throws DLEQVerificationException when a DLEQ proof exists but verification fails
     */
    boolean verifyBlindSignature(
            BlindSignature blindSignature,
            PublicKey blindedMessage,
            PublicKey mintPublicKey
    );

    /**
     * Verifies the optional DLEQ proof attached to a proof (Carol path).
     *
     * @param proof the proof that may contain a DLEQ proof (including blinding factor r)
     * @param mintPublicKey the mint's public key for the matching amount (A)
     * @return true when either no DLEQ proof is present or verification succeeds
     * @throws DLEQVerificationException when a DLEQ proof exists but verification fails
     */
    <T extends Secret> boolean verifyProof(
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
