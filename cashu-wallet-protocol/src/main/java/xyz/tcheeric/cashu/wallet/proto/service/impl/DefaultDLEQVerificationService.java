package xyz.tcheeric.cashu.wallet.proto.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.nut12.DLEQProof;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.crypto.DLEQUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.wallet.proto.service.DLEQVerificationException;
import xyz.tcheeric.cashu.wallet.proto.service.DLEQVerificationService;

import java.util.Objects;

/**
 * Default implementation for verifying and attaching DLEQ proofs (NUT-12).
 */
@Slf4j
public final class DefaultDLEQVerificationService implements DLEQVerificationService {

    private static final ECNamedCurveParameterSpec CURVE = ECNamedCurveTable.getParameterSpec("secp256k1");

    @Override
    public boolean verifyBlindSignature(
            BlindSignature blindSignature,
            PublicKey blindedMessage,
            PublicKey mintPublicKey
    ) {
        Objects.requireNonNull(blindSignature, "Blind signature cannot be null");

        if (!blindSignature.hasDLEQProof()) {
            log.debug("dleq_verification blind_signature_skipped reason=missing_proof keyset={} amount={}",
                blindSignature.getKeySetId(), blindSignature.getAmount());
            return true;
        }

        Objects.requireNonNull(blindedMessage, "Blinded message cannot be null when verifying DLEQ");
        Objects.requireNonNull(mintPublicKey, "Mint public key cannot be null when verifying DLEQ");
        Objects.requireNonNull(blindSignature.getDleq(), "Blind signature DLEQ proof cannot be null");

        ECPoint blindedMessagePoint = decodePublicKey(blindedMessage, "blinded message");
        ECPoint blindSignaturePoint = decodeSignature(blindSignature.getBlindedSignature());
        ECPoint mintPublicKeyPoint = decodePublicKey(mintPublicKey, "mint public key");

        boolean valid;
        try {
            DLEQProof dleq = blindSignature.getDleq();
            valid = DLEQUtils.verifyProof(
                dleq.getE(),
                dleq.getS(),
                blindedMessagePoint,
                blindSignaturePoint,
                mintPublicKeyPoint
            );
        } catch (Exception e) {
            throw new DLEQVerificationException(
                "Failed to verify DLEQ proof for blind signature keyset=" + blindSignature.getKeySetId() +
                    ". Encoded points were invalid. " +
                    "Suggestion: discard the mint response and retry minting.",
                e
            );
        }

        if (!valid) {
            throw new DLEQVerificationException(
                "Failed to verify DLEQ proof for blind signature keyset=" + blindSignature.getKeySetId() +
                    ". Proof verification returned false. " +
                    "Suggestion: request a new signature from the mint and retry."
            );
        }

        log.debug("dleq_verification blind_signature_valid keyset={} amount={}",
            blindSignature.getKeySetId(), blindSignature.getAmount());
        return true;
    }

    @Override
    public <T extends Secret> boolean verifyProof(
            Proof<T> proof,
            PublicKey mintPublicKey
    ) {
        Objects.requireNonNull(proof, "Proof cannot be null");
        Objects.requireNonNull(mintPublicKey, "Mint public key cannot be null");

        if (!proof.hasDLEQProof()) {
            log.debug("dleq_verification proof_skipped reason=missing_proof keyset={} amount={}",
                proof.getKeySetId(), proof.getAmount());
            return true;
        }

        Objects.requireNonNull(proof.getSecret(), "Proof secret cannot be null when verifying DLEQ");
        Objects.requireNonNull(proof.getUnblindedSignature(),
            "Proof signature cannot be null when verifying DLEQ");

        DLEQProof dleq = proof.getDleq();
        if (!dleq.hasBlindingFactor()) {
            throw new DLEQVerificationException(
                "Failed to verify DLEQ proof for proof keyset=" + proof.getKeySetId() +
                    ". Blinding factor (r) is missing. " +
                    "Suggestion: request the sender to include NUT-12 DLEQ data."
            );
        }

        ECPoint mintPublicKeyPoint = decodePublicKey(mintPublicKey, "mint public key");
        ECPoint unblindedSignaturePoint = decodeSignature(proof.getUnblindedSignature());

        boolean valid;
        try {
            valid = DLEQUtils.verifyProofWithBlindingFactor(
                dleq.getE(),
                dleq.getS(),
                dleq.getR(),
                proof.getSecret().toBytes(),
                unblindedSignaturePoint,
                mintPublicKeyPoint
            );
        } catch (Exception e) {
            throw new DLEQVerificationException(
                "Failed to verify DLEQ proof for proof keyset=" + proof.getKeySetId() +
                    ". Proof inputs were malformed. " +
                    "Suggestion: discard the token and retry with a fresh copy.",
                e
            );
        }

        if (!valid) {
            throw new DLEQVerificationException(
                "Failed to verify DLEQ proof for proof keyset=" + proof.getKeySetId() +
                    ". Proof verification returned false. " +
                    "Suggestion: ask the sender to refresh the token or remint."
            );
        }

        log.debug("dleq_verification proof_valid keyset={} amount={}", proof.getKeySetId(), proof.getAmount());
        return true;
    }

    @Override
    public <T extends Secret> Proof<T> addDLEQToProof(
            Proof<T> proof,
            byte[] blindingFactor,
            DLEQProof blindSignatureProof
    ) {
        Objects.requireNonNull(proof, "Proof cannot be null");

        if (blindSignatureProof == null) {
            return proof;
        }

        if (blindingFactor == null || blindingFactor.length != 32) {
            throw new IllegalArgumentException(String.format(
                "Blinding factor must be 32 bytes for DLEQ; got: %s",
                blindingFactor == null ? "null" : blindingFactor.length
            ));
        }

        DLEQProof proofDleq = DLEQProof.forProof(
            blindSignatureProof.getE(),
            blindSignatureProof.getS(),
            Utils.bytesToHexString(blindingFactor)
        );

        return Proof.<T>builder()
            .amount(proof.getAmount())
            .secret(proof.getSecret())
            .keySetId(proof.getKeySetId())
            .unblindedSignature(proof.getUnblindedSignature())
            .dleq(proofDleq)
            .witness(proof.getWitness())
            .build();
    }

    private ECPoint decodeSignature(Signature signature) {
        Objects.requireNonNull(signature, "Signature cannot be null when decoding EC point");
        return decodePoint(signature.getCompressedBytes(), "signature");
    }

    private ECPoint decodePublicKey(PublicKey publicKey, String description) {
        Objects.requireNonNull(publicKey, "Public key cannot be null when decoding EC point");
        return decodePoint(publicKey.getBytes(), description);
    }

    private ECPoint decodePoint(byte[] encoded, String description) {
        Objects.requireNonNull(encoded, "Encoded point cannot be null");
        try {
            return CURVE.getCurve().decodePoint(encoded).normalize();
        } catch (IllegalArgumentException e) {
            throw new DLEQVerificationException(
                "Failed to decode " + description + " for DLEQ verification. Encoded value was invalid. " +
                    "Suggestion: retry with a fresh token or mint response.",
                e
            );
        }
    }
}
