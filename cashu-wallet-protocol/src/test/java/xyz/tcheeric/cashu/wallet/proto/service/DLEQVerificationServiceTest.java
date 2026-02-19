package xyz.tcheeric.cashu.wallet.proto.service;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.nut12.DLEQProof;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.DLEQUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.wallet.proto.service.impl.DefaultDLEQVerificationService;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DLEQVerificationService")
class DLEQVerificationServiceTest {

    private static final ECNamedCurveParameterSpec CURVE = ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final KeysetId KEYSET_ID = KeysetId.fromString("009a1f293253e41e");

    private DLEQVerificationService service;

    @BeforeEach
    void setup() {
        service = new DefaultDLEQVerificationService();
    }

    @Nested
    @DisplayName("Alice verification")
    class AliceVerification {

        /**
         * Ensures blind signatures with valid DLEQ proofs verify successfully.
         */
        @Test
        void shouldVerifyBlindSignatureWhenProofValid() {
            DleqFixture fixture = createFixture();

            assertThat(service.verifyBlindSignature(
                fixture.blindSignature(),
                fixture.blindedMessage(),
                fixture.mintPublicKey()
            )).isTrue();
        }

        /**
         * Ensures invalid DLEQ data on blind signatures is rejected.
         */
        @Test
        void shouldRejectBlindSignatureWhenProofInvalid() {
            DleqFixture fixture = createFixture();
            DLEQProof tamperedDleq = DLEQProof.forBlindSignature(
                tamperHex(fixture.blindSignature().getDleq().getE()),
                fixture.blindSignature().getDleq().getS()
            );

            BlindSignature tampered = BlindSignature.builder()
                .amount(fixture.blindSignature().getAmount())
                .keySetId(fixture.blindSignature().getKeySetId())
                .blindedSignature(fixture.blindSignature().getBlindedSignature())
                .dleq(tamperedDleq)
                .build();

            assertThatThrownBy(() -> service.verifyBlindSignature(
                tampered,
                fixture.blindedMessage(),
                fixture.mintPublicKey()
            )).isInstanceOf(DLEQVerificationException.class);
        }

        /**
         * Ensures blind signatures without DLEQ data bypass verification.
         */
        @Test
        void shouldSkipBlindSignatureWhenProofMissing() {
            DleqFixture fixture = createFixture();

            BlindSignature withoutProof = BlindSignature.builder()
                .amount(fixture.blindSignature().getAmount())
                .keySetId(fixture.blindSignature().getKeySetId())
                .blindedSignature(fixture.blindSignature().getBlindedSignature())
                .build();

            assertThat(service.verifyBlindSignature(
                withoutProof,
                fixture.blindedMessage(),
                fixture.mintPublicKey()
            )).isTrue();
        }
    }

    @Nested
    @DisplayName("Carol verification")
    class CarolVerification {

        /**
         * Ensures proofs with valid DLEQ data and blinding factor verify successfully.
         */
        @Test
        void shouldVerifyProofWhenProofValid() {
            DleqFixture fixture = createFixture();

            assertThat(service.verifyProof(fixture.proof(), fixture.mintPublicKey()))
                .isTrue();
        }

        /**
         * Ensures proofs missing the blinding factor fail verification.
         */
        @Test
        void shouldRejectProofWhenBlindingFactorMissing() {
            DleqFixture fixture = createFixture();

            Proof<DeterministicSecret> missingR = Proof.<DeterministicSecret>builder()
                .amount(fixture.proof().getAmount())
                .secret(fixture.proof().getSecret())
                .keySetId(fixture.proof().getKeySetId())
                .unblindedSignature(fixture.proof().getUnblindedSignature())
                .dleq(DLEQProof.forBlindSignature(
                    fixture.proof().getDleq().getE(),
                    fixture.proof().getDleq().getS()
                ))
                .build();

            assertThatThrownBy(() -> service.verifyProof(missingR, fixture.mintPublicKey()))
                .isInstanceOf(DLEQVerificationException.class)
                .hasMessageContaining("Blinding factor");
        }

        /**
         * Ensures invalid DLEQ data on received proofs is rejected.
         */
        @Test
        void shouldRejectProofWhenProofInvalid() {
            DleqFixture fixture = createFixture();
            DLEQProof tampered = DLEQProof.forProof(
                tamperHex(fixture.proof().getDleq().getE()),
                fixture.proof().getDleq().getS(),
                fixture.proof().getDleq().getR()
            );

            Proof<DeterministicSecret> badProof = Proof.<DeterministicSecret>builder()
                .amount(fixture.proof().getAmount())
                .secret(fixture.proof().getSecret())
                .keySetId(fixture.proof().getKeySetId())
                .unblindedSignature(fixture.proof().getUnblindedSignature())
                .dleq(tampered)
                .build();

            assertThatThrownBy(() -> service.verifyProof(badProof, fixture.mintPublicKey()))
                .isInstanceOf(DLEQVerificationException.class);
        }
    }

    @Nested
    @DisplayName("Attachment")
    class Attachment {

        /**
         * Ensures DLEQ metadata is attached to proofs with the provided blinding factor.
         */
        @Test
        void shouldAttachDleqDataToProof() {
            DleqFixture fixture = createFixture();

            Proof<DeterministicSecret> baseProof = Proof.<DeterministicSecret>builder()
                .amount(fixture.proof().getAmount())
                .secret(fixture.proof().getSecret())
                .keySetId(fixture.proof().getKeySetId())
                .unblindedSignature(fixture.proof().getUnblindedSignature())
                .build();

            Proof<DeterministicSecret> withDleq = service.addDLEQToProof(
                baseProof,
                fixture.blindingFactor(),
                fixture.blindSignature().getDleq()
            );

            assertThat(withDleq.getDleq()).isNotNull();
            assertThat(withDleq.getDleq().getR()).isEqualTo(Utils.bytesToHexString(fixture.blindingFactor()));
            assertThat(withDleq.getDleq().getE()).isEqualTo(fixture.blindSignature().getDleq().getE());
        }
    }

    private DleqFixture createFixture() {
        byte[] secretBytes = new byte[32];
        secretBytes[0] = 1;
        byte[] blindingFactor = new byte[32];
        blindingFactor[0] = 2;

        DeterministicSecret secret = DeterministicSecret.create(secretBytes, KEYSET_ID, 0);

        BigInteger privateKey = BigInteger.valueOf(7L);
        BigInteger r = Utils.bigIntFromBytes(blindingFactor);

        ECPoint mintPublicKeyPoint = CURVE.getG().multiply(privateKey).normalize();
        PublicKey mintPublicKey = PublicKey.fromPoint(mintPublicKeyPoint);

        ECPoint Y = BDHKEUtils.hashToCurve(secretBytes).normalize();
        ECPoint blindedMessagePoint = Y.add(CURVE.getG().multiply(r)).normalize();
        PublicKey blindedMessage = PublicKey.fromPoint(blindedMessagePoint);

        ECPoint blindSignaturePoint = BDHKEUtils.signBlindedMessage(blindedMessagePoint, privateKey).normalize();
        var proofResult = DLEQUtils.generateProof(privateKey, blindedMessagePoint, blindSignaturePoint);

        BlindSignature blindSignature = BlindSignature.builder()
            .amount(1)
            .keySetId(KEYSET_ID)
            .blindedSignature(Signature.fromBytes(blindSignaturePoint.getEncoded(true)))
            .dleq(DLEQProof.forBlindSignature(proofResult.e(), proofResult.s()))
            .build();

        ECPoint unblindedSignaturePoint = BDHKEUtils.unblindSignature(blindSignaturePoint, r, mintPublicKeyPoint)
            .normalize();

        Proof<DeterministicSecret> proof = Proof.<DeterministicSecret>builder()
            .amount(1)
            .secret(secret)
            .keySetId(KEYSET_ID.toString())
            .unblindedSignature(Signature.fromBytes(unblindedSignaturePoint.getEncoded(true)))
            .dleq(DLEQProof.forProof(
                proofResult.e(),
                proofResult.s(),
                Utils.bytesToHexString(blindingFactor)
            ))
            .build();

        return new DleqFixture(blindSignature, proof, mintPublicKey, blindedMessage, blindingFactor);
    }

    private String tamperHex(String hex) {
        char first = hex.charAt(0);
        char toggled = first == '0' ? '1' : '0';
        return toggled + hex.substring(1);
    }

    private record DleqFixture(
        BlindSignature blindSignature,
        Proof<DeterministicSecret> proof,
        PublicKey mintPublicKey,
        PublicKey blindedMessage,
        byte[] blindingFactor
    ) {
    }
}
