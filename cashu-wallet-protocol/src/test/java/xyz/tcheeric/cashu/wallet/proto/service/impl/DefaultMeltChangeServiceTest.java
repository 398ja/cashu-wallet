package xyz.tcheeric.cashu.wallet.proto.service.impl;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltResponse;
import xyz.tcheeric.cashu.wallet.proto.service.MeltChangeService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultMeltChangeService}, covering NUT-08 change unblinding.
 *
 * <p>The fixture plays the mint: it holds a private key per amount, imprints amounts on the
 * blank outputs, and signs them, so the unblinded change can be verified with real BDHKE.
 */
class DefaultMeltChangeServiceTest {

    private static final ECNamedCurveParameterSpec CURVE = ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final KeysetId KEYSET_ID = KeysetId.fromString("009a1f293253e41e");
    private static final List<Integer> KEYSET_AMOUNTS = List.of(1, 2, 4, 8, 16, 32, 64, 128, 256, 512);

    private MeltChangeService service;
    private KeySet keySet;

    @BeforeEach
    void setUp() {
        service = new DefaultMeltChangeService();
        keySet = createKeySet();
    }

    /**
     * Ensures the change signatures the mint returns are unblinded into proofs that carry
     * the imprinted amounts, recovering the unspent part of the fee reserve.
     */
    @Test
    void shouldRecoverChangeProofsWhenMintReturnsChange() {
        List<DeterministicSecret> secrets = secrets(10);
        List<byte[]> blindingFactors = blindingFactors(10);
        List<Integer> changeAmounts = List.of(4, 128, 256, 512);

        PostMeltResponse response = mintResponseWithChange(secrets, blindingFactors, changeAmounts);

        List<Proof<DeterministicSecret>> changeProofs =
            service.recoverChange(response, secrets, blindingFactors, keySet);

        assertThat(changeProofs).hasSize(4);
        assertThat(changeProofs).extracting(Proof::getAmount).containsExactly(4, 128, 256, 512);
        assertThat(changeProofs.stream().mapToInt(Proof::getAmount).sum()).isEqualTo(900);
    }

    /**
     * Ensures each recovered change proof carries a signature that verifies under the mint key,
     * proving the unblinding was correct and the proof is actually spendable.
     */
    @Test
    void shouldProduceVerifiableSignaturesWhenUnblindingChange() {
        List<DeterministicSecret> secrets = secrets(2);
        List<byte[]> blindingFactors = blindingFactors(2);
        List<Integer> changeAmounts = List.of(2);

        PostMeltResponse response = mintResponseWithChange(secrets, blindingFactors, changeAmounts);

        List<Proof<DeterministicSecret>> changeProofs =
            service.recoverChange(response, secrets, blindingFactors, keySet);

        Proof<DeterministicSecret> changeProof = changeProofs.get(0);
        boolean verified = BDHKEUtils.verify(
            changeProof.getSecret().toString(),
            privateKeyFor(changeProof.getAmount()),
            CURVE.getCurve().decodePoint(changeProof.getUnblindedSignature().getCompressedBytes()).normalize()
        );

        assertThat(verified).isTrue();
    }

    /**
     * Ensures a melt without change yields no proofs rather than failing.
     */
    @Test
    void shouldReturnNoProofsWhenMintReturnsNoChange() {
        PostMeltResponse response = new PostMeltResponse(true, "preimage");

        assertThat(service.recoverChange(response, secrets(4), blindingFactors(4), keySet)).isEmpty();
    }

    /**
     * Ensures more change signatures than blank outputs is rejected, since the extra
     * signatures cannot be matched to any wallet secret.
     */
    @Test
    void shouldRejectChangeWhenMintReturnsMoreSignaturesThanBlankOutputs() {
        List<DeterministicSecret> secrets = secrets(4);
        List<byte[]> blindingFactors = blindingFactors(4);
        PostMeltResponse response =
            mintResponseWithChange(secrets, blindingFactors, List.of(1, 2, 4, 8));
        List<BlindSignature> tooManySignatures = new ArrayList<>(response.getChange());
        tooManySignatures.add(response.getChange().get(0));
        response.setChange(tooManySignatures);

        assertThatThrownBy(() -> service.recoverChange(response, secrets, blindingFactors, keySet))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank outputs");
    }

    private PostMeltResponse mintResponseWithChange(
            List<DeterministicSecret> secrets,
            List<byte[]> blindingFactors,
            List<Integer> changeAmounts) {

        List<BlindSignature> change = new ArrayList<>(changeAmounts.size());
        for (int index = 0; index < changeAmounts.size(); index++) {
            change.add(signBlankOutput(
                secrets.get(index), blindingFactors.get(index), changeAmounts.get(index)));
        }
        return new PostMeltResponse(true, "preimage", change);
    }

    private BlindSignature signBlankOutput(DeterministicSecret secret, byte[] blindingFactor, int amount) {
        byte[] blindedBytes = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);
        ECPoint blindedPoint = CURVE.getCurve()
            .decodePoint(PublicKey.fromBytes(blindedBytes, false).getBytes())
            .normalize();
        ECPoint signaturePoint = BDHKEUtils.signBlindedMessage(blindedPoint, privateKeyFor(amount)).normalize();

        return BlindSignature.builder()
            .amount(amount)
            .keySetId(KEYSET_ID)
            .blindedSignature(Signature.fromBytes(signaturePoint.getEncoded(true)))
            .build();
    }

    private KeySet createKeySet() {
        Keys keys = new Keys();
        KEYSET_AMOUNTS.forEach(amount -> keys.put(
            BigInteger.valueOf(amount),
            PublicKey.fromPoint(CURVE.getG().multiply(privateKeyFor(amount)).normalize())
        ));

        KeySet created = new KeySet();
        created.setId(KEYSET_ID.toString());
        created.setUnit("sat");
        created.setKeys(keys);
        return created;
    }

    private BigInteger privateKeyFor(int amount) {
        return BigInteger.valueOf(amount + 1L);
    }

    private List<DeterministicSecret> secrets(int count) {
        List<DeterministicSecret> secrets = new ArrayList<>(count);
        for (int counter = 0; counter < count; counter++) {
            byte[] data = new byte[32];
            data[31] = (byte) (counter + 1);
            secrets.add(DeterministicSecret.create(data, KEYSET_ID, counter));
        }
        return secrets;
    }

    private List<byte[]> blindingFactors(int count) {
        List<byte[]> factors = new ArrayList<>(count);
        for (int counter = 0; counter < count; counter++) {
            byte[] factor = new byte[32];
            factor[31] = (byte) (counter + 3);
            factors.add(factor);
        }
        return factors;
    }
}
