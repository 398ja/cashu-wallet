package xyz.tcheeric.cashu.wallet.client.service;

import org.bitcoinj.crypto.DeterministicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Keys;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteResponse;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltRequest;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltResponse;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.wallet.client.impl.RequestMeltToken;
import xyz.tcheeric.cashu.wallet.proto.builders.BlankOutputBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.impl.DefaultMeltChangeService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WalletMeltServiceImpl}, exercising the NUT-08 fee return end to end
 * against a stub mint that plays the mint's half of the protocol with real BDHKE.
 */
class WalletMeltServiceTest {

    private static final ECNamedCurveParameterSpec CURVE = ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon "
        + "abandon abandon abandon abandon abandon about";
    private static final String TEST_MINT_URL = "https://mint.example.com";
    private static final KeysetId KEYSET_ID = KeysetId.fromString("009a1f293253e41e");
    private static final List<Integer> KEYSET_AMOUNTS =
        List.of(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024);

    private DeterministicKey masterKey;
    private KeySet keySet;

    @BeforeEach
    void setUp() {
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, "");
        keySet = createKeySet();
    }

    /**
     * Exercises the W3 exit criterion: a melt whose actual routing fee is below the quoted
     * fee reserve recovers the difference, and the wallet balance after the melt reflects it.
     *
     * <p>The wallet spends 101 000 sat of proofs for a 100 000 sat invoice with a 1000 sat
     * reserve. The mint routes for 100 sat and returns 900 sat of change. Without NUT-08
     * blank outputs the wallet would keep a zero balance and forfeit all 900 sat.
     */
    @Test
    void shouldRecoverUnspentFeeReserveIntoBalanceWhenRoutingFeeIsBelowReserve() {
        int invoiceAmount = 100_000;
        int feeReserve = 1_000;
        int actualRoutingFee = 100;
        int expectedChange = feeReserve - actualRoutingFee;

        StubMint mint = new StubMint(expectedChange);
        WalletMeltService service = new WalletMeltServiceImpl(
            TEST_MINT_URL, new BlankOutputBuilder(), new DefaultMeltChangeService(), mint);

        int balanceBeforeMelt = invoiceAmount + feeReserve;
        MeltResult result = service.melt(
            quote(invoiceAmount, feeReserve),
            inputs(balanceBeforeMelt),
            masterKey,
            keySet,
            0,
            PaymentMethod.BOLT11);

        assertThat(mint.blankOutputCount()).isEqualTo(10);
        assertThat(result.isPaid()).isTrue();
        assertThat(result.getChangeAmount()).isEqualTo(expectedChange);

        int balanceAfterMelt = result.getChangeAmount();
        assertThat(balanceAfterMelt)
            .isEqualTo(balanceBeforeMelt - invoiceAmount - actualRoutingFee)
            .isEqualTo(900);
    }

    /**
     * Ensures the derivation counter advances past every blank output sent, so a later mint
     * never reuses a secret that the mint already signed as change.
     */
    @Test
    void shouldAdvanceCounterPastBlankOutputsWhenMelting() {
        StubMint mint = new StubMint(4);
        WalletMeltService service = new WalletMeltServiceImpl(
            TEST_MINT_URL, new BlankOutputBuilder(), new DefaultMeltChangeService(), mint);

        MeltResult result = service.melt(
            quote(100, 8), inputs(108), masterKey, keySet, 7, PaymentMethod.BOLT11);

        assertThat(mint.blankOutputCount()).isEqualTo(3);
        assertThat(result.getNextCounter()).isEqualTo(10);
    }

    /**
     * Ensures a zero fee reserve sends no blank outputs, as NUT-08 prescribes, and leaves
     * the derivation counter untouched.
     */
    @Test
    void shouldSendNoBlankOutputsWhenFeeReserveIsZero() {
        StubMint mint = new StubMint(0);
        WalletMeltService service = new WalletMeltServiceImpl(
            TEST_MINT_URL, new BlankOutputBuilder(), new DefaultMeltChangeService(), mint);

        MeltResult result = service.melt(
            quote(100, 0), inputs(100), masterKey, keySet, 5, PaymentMethod.BOLT11);

        assertThat(mint.blankOutputCount()).isZero();
        assertThat(result.getChangeProofs()).isEmpty();
        assertThat(result.getNextCounter()).isEqualTo(5);
    }

    private PostMeltQuoteResponse quote(int amount, int feeReserve) {
        return new PostMeltQuoteResponse("quote-id", amount, feeReserve, false, 0);
    }

    private List<Proof<DeterministicSecret>> inputs(int amount) {
        byte[] secretData = new byte[32];
        secretData[31] = 42;
        return List.of(Proof.<DeterministicSecret>builder()
            .amount(amount)
            .secret(DeterministicSecret.create(secretData, KEYSET_ID, 0))
            .keySetId(KEYSET_ID.toString())
            .build());
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

    private static BigInteger privateKeyFor(int amount) {
        return BigInteger.valueOf(amount + 1L);
    }

    /**
     * A mint that imprints the overpaid fee onto the blank outputs it received, decomposing
     * it into powers of two and signing them, exactly as NUT-08 describes.
     */
    private static final class StubMint implements MeltClientFactory {

        private final int changeAmount;
        private int blankOutputCount;

        private StubMint(int changeAmount) {
            this.changeAmount = changeAmount;
        }

        private int blankOutputCount() {
            return blankOutputCount;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Secret> RequestMeltToken<T> create(
                String mintUrl, PaymentMethod paymentMethod, PostMeltRequest<T> request) {

            List<BlindedMessage> blankOutputs =
                request.getOutputs() == null ? List.of() : request.getOutputs();
            blankOutputCount = blankOutputs.size();

            PostMeltResponse response =
                new PostMeltResponse(true, "preimage", imprintChange(blankOutputs));
            return (RequestMeltToken<T>) new StubMeltToken(mintUrl, paymentMethod, request, response);
        }

        private List<BlindSignature> imprintChange(List<BlindedMessage> blankOutputs) {
            List<Integer> amounts = decomposeToPowersOfTwo(changeAmount);
            List<BlindSignature> change = new ArrayList<>(amounts.size());

            for (int index = 0; index < amounts.size() && index < blankOutputs.size(); index++) {
                change.add(sign(blankOutputs.get(index), amounts.get(index)));
            }
            return change;
        }

        private BlindSignature sign(BlindedMessage blankOutput, int amount) {
            ECPoint blindedPoint = CURVE.getCurve()
                .decodePoint(blankOutput.getBlindedMessage().getBytes())
                .normalize();
            ECPoint signaturePoint =
                BDHKEUtils.signBlindedMessage(blindedPoint, privateKeyFor(amount)).normalize();

            return BlindSignature.builder()
                .amount(amount)
                .keySetId(blankOutput.getKeySetId())
                .blindedSignature(Signature.fromBytes(signaturePoint.getEncoded(true)))
                .build();
        }

        private List<Integer> decomposeToPowersOfTwo(int amount) {
            List<Integer> amounts = new ArrayList<>();
            for (int bit = 0; bit < Integer.SIZE - 1; bit++) {
                int value = 1 << bit;
                if ((amount & value) != 0) {
                    amounts.add(value);
                }
            }
            return amounts;
        }
    }

    /**
     * A melt client that answers with a canned mint response instead of performing HTTP.
     */
    private static final class StubMeltToken extends RequestMeltToken<DeterministicSecret> {

        private final PostMeltResponse response;

        @SuppressWarnings("unchecked")
        private StubMeltToken(
                String mintUrl,
                PaymentMethod paymentMethod,
                PostMeltRequest<?> request,
                PostMeltResponse response) {
            super(mintUrl, paymentMethod, (PostMeltRequest<DeterministicSecret>) request);
            this.response = response;
        }

        @Override
        public PostMeltResponse execute() {
            return response;
        }
    }
}
