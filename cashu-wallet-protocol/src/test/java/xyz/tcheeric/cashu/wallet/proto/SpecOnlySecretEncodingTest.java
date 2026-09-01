package xyz.tcheeric.cashu.wallet.proto;

import org.bitcoinj.crypto.DeterministicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.util.SecretUtil;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.SecretEncoding;
import xyz.tcheeric.cashu.wallet.proto.tasks.DeriveSecretsTask;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interoperability tests asserting that a wallet-issued proof is acceptable to a third party.
 *
 * <p>Every other test in this module mints and verifies inside one process with the same bytes on
 * both sides, so the wallet stays internally consistent even when its encoding disagrees with the
 * spec. That is precisely how the cashu-lib 0.22.0 fund-loss regression stayed green (issue #40).
 * These tests instead ask the question only an outside verifier asks: does the wallet's curve point
 * match the one a SPEC-only mint such as Nutshell or cashu-ts computes from the wire string?
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/00.md">NUT-00</a>
 */
class SpecOnlySecretEncodingTest {

    private static final ECNamedCurveParameterSpec CURVE = ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_KEYSET_ID = "009a1f293253e41e";
    private static final BigInteger MINT_PRIVATE_KEY = BigInteger.valueOf(255L);

    private DeterministicKey masterKey;
    private KeysetId keysetId;

    @BeforeEach
    void setUp() {
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, "");
        keysetId = KeysetId.fromString(TEST_KEYSET_ID);
    }

    /**
     * Ensures the bytes the wallet blinds are the bytes a SPEC-only verifier hashes.
     *
     * <p>Every issuance site blinds with {@code secret.getData()}, so if those bytes are not the
     * UTF-8 encoding of the wire string, the proof commits to a curve point no spec-conforming mint
     * can reproduce, and the funds are unspendable outside this library.
     */
    @Test
    void shouldBlindTheBytesASpecOnlyVerifierHashesWhenIssuingAProof() {
        DeterministicSecret secret = deriveFirstSecret();

        assertThat(secret.getData())
            .as("Blinded bytes must be the SPEC encoding of the transmitted secret string")
            .isEqualTo(SecretEncoding.SPEC.encode(secret.toString()));
    }

    /**
     * Ensures the Y reported to /checkstate is the Y the proof commits to.
     *
     * <p>When these diverge the mint recognises no proof, the lookup degrades to {@code null}, and
     * a spent proof is reported back as spendable balance.
     */
    @Test
    void shouldReportTheCommittedCurvePointWhenCheckingProofState() {
        DeterministicSecret secret = deriveFirstSecret();

        String yProofCommitsTo = BDHKEUtils.pointToHex(BDHKEUtils.hashToCurve(secret.getData()));

        assertThat(SecretUtil.toY(secret))
            .as("The Y sent to the mint must be the Y the proof commits to")
            .isEqualTo(yProofCommitsTo);
    }

    /**
     * Ensures a full blind, sign and unblind round trip yields a signature a SPEC-only mint accepts.
     *
     * <p>Verification here deliberately bypasses {@link SecretEncoding#verificationOrder()} and
     * checks {@code C = k * hash_to_curve(SPEC(secret))} directly, because the legacy fallback would
     * otherwise mask an encoding that no other implementation accepts.
     */
    @Test
    void shouldProduceASignatureASpecOnlyMintAcceptsWhenRoundTrippingAProof() {
        DeriveSecretsTask.DeriveSecretsResult derived = new DeriveSecretsTask(masterKey, keysetId, 0, 1).execute();
        DeterministicSecret secret = derived.getSecrets().get(0);
        byte[] blindingFactor = derived.getBlindingFactors().get(0);

        ECPoint unblindedSignature = mintAndUnblind(secret, blindingFactor);
        ECPoint expected = BDHKEUtils
            .hashToCurve(SecretEncoding.SPEC.encode(secret.toString()))
            .multiply(MINT_PRIVATE_KEY)
            .normalize();

        assertThat(unblindedSignature.normalize())
            .as("A SPEC-only mint must accept the wallet's proof without the legacy fallback")
            .isEqualTo(expected);
    }

    /** Plays the mint: blinds with the wallet's bytes, signs, and unblinds with the mint key. */
    private ECPoint mintAndUnblind(DeterministicSecret secret, byte[] blindingFactor) {
        byte[] mintPrivateKey = BigIntegers.asUnsignedByteArray(32, MINT_PRIVATE_KEY);
        byte[] mintPublicKey = CURVE.getG().multiply(MINT_PRIVATE_KEY).getEncoded(true);

        byte[] blindedMessage = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);
        byte[] blindSignature = BDHKEUtils.signBlindedMessage(blindedMessage, mintPrivateKey);
        byte[] unblindedSignature = BDHKEUtils.unblindSignature(blindSignature, blindingFactor, mintPublicKey);

        return CURVE.getCurve().decodePoint(unblindedSignature);
    }

    private DeterministicSecret deriveFirstSecret() {
        List<DeterministicSecret> secrets = new DeriveSecretsTask(masterKey, keysetId, 0, 1).execute().getSecrets();
        return secrets.get(0);
    }
}
