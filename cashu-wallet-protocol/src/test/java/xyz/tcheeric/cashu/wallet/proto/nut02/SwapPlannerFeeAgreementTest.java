package xyz.tcheeric.cashu.wallet.proto.nut02;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.LiteralSecret;
import xyz.tcheeric.cashu.common.PrivateKey;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.nut02.KeySetResolver;
import xyz.tcheeric.cashu.entities.rest.nut03.PostSwapRequest;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the wallet's swap arithmetic against the rule the mint enforces, rather than against the
 * wallet's own idea of it.
 *
 * <p>Every assertion here runs the plan the wallet produced through {@code sum(inputs) - fees ==
 * sum(outputs)}, computing the fee with {@code PostSwapRequest.getFees}, which is the very call
 * the mint's {@code VerifyFeesTask} makes. The keysets it is priced against are built from the
 * mint's published listing, not from the wallet's cache, so a wallet that is merely
 * self-consistent still fails here. Getting the fee wrong in either direction makes the mint
 * reject the swap, so the amounts are asserted exactly.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02</a>
 */
class SwapPlannerFeeAgreementTest {

    private static final String ACTIVE_KEYSET_ID = "004cf8cba2f93266";
    private static final String RETIRED_KEYSET_ID = "009a1f293253e41e";
    private static final String UNIT = "sat";

    /** One satoshi per input spent, so two inputs cost exactly two satoshis. */
    private static final int ONE_SAT_PER_INPUT_PPK = 1000;

    private static final List<Integer> DENOMINATIONS = List.of(1, 2, 4, 8, 16, 32, 64);

    private KeysetCache keysetCache;
    private SwapPlanner planner;

    @BeforeEach
    void setUp() {
        keysetCache = new KeysetCache();
        planner = new SwapPlanner(keysetCache);
    }

    /**
     * The acceptance criterion of issue #36. Plans a swap of two 8-sat proofs against a keyset
     * charging a satoshi per input, and asserts the mint's own balance check passes on the
     * resulting request: 16 in, 2 of fee, 14 out.
     */
    @Test
    void shouldBuildOutputsTheMintAcceptsWhenTheKeysetChargesAFee() {
        publishKeysets(activeKeyset(ONE_SAT_PER_INPUT_PPK));

        SwapPlan<LiteralSecret> plan = planner.planSwap(
                List.of(proof(8, ACTIVE_KEYSET_ID), proof(8, ACTIVE_KEYSET_ID)), UNIT, DENOMINATIONS);

        assertThat(plan.getInputTotal()).isEqualTo(16);
        assertThat(plan.getFee()).isEqualTo(2);
        assertThat(plan.getOutputTotal()).isEqualTo(14);
        assertThat(plan.getOutputAmounts()).containsExactly(8, 4, 2);
        assertThatTheMintWouldAccept(plan);
    }

    /**
     * Checks that a fee below one satoshi per input is rounded up rather than truncated away.
     * Two inputs at 1 ppk owe a whole satoshi, so 16 in must produce 15 out; a wallet that
     * truncates to zero asks for all 16 back and the mint refuses the swap.
     */
    @Test
    void shouldRoundAFractionalFeeUpToAWholeUnitLikeTheMintDoes() {
        publishKeysets(activeKeyset(1));

        SwapPlan<LiteralSecret> plan = planner.planSwap(
                List.of(proof(8, ACTIVE_KEYSET_ID), proof(8, ACTIVE_KEYSET_ID)), UNIT, DENOMINATIONS);

        assertThat(plan.getFee()).isEqualTo(1);
        assertThat(plan.getOutputTotal()).isEqualTo(15);
        assertThatTheMintWouldAccept(plan);
    }

    /**
     * Checks that a keyset nobody has priced still swaps for the full amount. Fees are off unless
     * an operator configures one, and a wallet that withholds a satoshi anyway hands the mint an
     * unbalanced request just as surely as one that underpays.
     */
    @Test
    void shouldChargeNothingWhenTheKeysetIsUnpriced() {
        publishKeysets(activeKeyset(0));

        SwapPlan<LiteralSecret> plan = planner.planSwap(
                List.of(proof(8, ACTIVE_KEYSET_ID), proof(8, ACTIVE_KEYSET_ID)), UNIT, DENOMINATIONS);

        assertThat(plan.getFee()).isZero();
        assertThat(plan.getOutputTotal()).isEqualTo(16);
        assertThatTheMintWouldAccept(plan);
    }

    /**
     * Checks that inputs spanning two differently priced keysets are each charged their own rate.
     * Pricing every input from the first one it sees mischarges the rest in one direction or the
     * other, and NUT-02 explicitly keeps a retired keyset's proofs spendable alongside fresh ones.
     */
    @Test
    void shouldPriceEachInputAgainstItsOwnKeysetWhenTheSwapMixesThem() {
        publishKeysets(activeKeyset(ONE_SAT_PER_INPUT_PPK), retiredKeyset(2000));

        SwapPlan<LiteralSecret> plan = planner.planSwap(
                List.of(proof(8, ACTIVE_KEYSET_ID), proof(8, RETIRED_KEYSET_ID)), UNIT, DENOMINATIONS);

        assertThat(plan.getFee())
                .as("1000 ppk plus 2000 ppk rounds to 3 sats, not 2 and not 4")
                .isEqualTo(3);
        assertThat(plan.getOutputTotal()).isEqualTo(13);
        assertThatTheMintWouldAccept(plan);
    }

    /**
     * Checks that outputs name the mint's active keyset even when every input came from a retired
     * one. A mint refuses to sign against an inactive keyset, so a wallet that echoes the input
     * keyset back cannot swap out of a rotation at all.
     */
    @Test
    void shouldBuildOutputsOnlyAgainstTheActiveKeyset() {
        publishKeysets(activeKeyset(0), retiredKeyset(0));

        SwapPlan<LiteralSecret> plan = planner.planSwap(
                List.of(proof(8, RETIRED_KEYSET_ID)), UNIT, DENOMINATIONS);

        assertThat(plan.getOutputKeysetId()).isEqualTo(ACTIVE_KEYSET_ID);
    }

    /**
     * Checks that a mint with nothing active is a hard failure. Falling back to a retired keyset
     * would produce outputs the mint refuses, and silently doing nothing would strand the funds.
     */
    @Test
    void shouldRefuseToPlanWhenNoKeysetIsActive() {
        publishKeysets(retiredKeyset(0));

        assertThatThrownBy(() -> planner.planSwap(
                List.of(proof(8, RETIRED_KEYSET_ID)), UNIT, DENOMINATIONS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active keyset");
    }

    /**
     * Checks that an input the wallet cannot price stops the swap. Treating an unknown keyset as
     * free is the silent path to an unbalanced request, and the mint's refusal would name the
     * balance rather than the missing keyset.
     */
    @Test
    void shouldRefuseToPlanWhenAnInputNamesAnUncachedKeyset() {
        publishKeysets(activeKeyset(ONE_SAT_PER_INPUT_PPK));

        assertThatThrownBy(() -> planner.planSwap(
                List.of(proof(8, RETIRED_KEYSET_ID)), UNIT, DENOMINATIONS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RETIRED_KEYSET_ID);
    }

    /**
     * Runs the plan through the mint's balance equation, computing the fee the way the mint does:
     * from the published keyset listing, via the same {@code getFees} call
     * {@code VerifyFeesTask} makes.
     */
    private void assertThatTheMintWouldAccept(SwapPlan<LiteralSecret> plan) {
        PostSwapRequest<LiteralSecret> request =
                new PostSwapRequest<>(plan.getInputs(), blindedMessagesFor(plan));

        int inputTotal = plan.getInputTotal();
        int outputTotal = request.getBlindedMessages().stream().mapToInt(BlindedMessage::getAmount).sum();
        int mintComputedFee = feeTheMintWouldCharge(request);

        assertThat(inputTotal - mintComputedFee)
                .as("the mint refuses anything but sum(inputs) - fees == sum(outputs)")
                .isEqualTo(outputTotal);
        assertThat(mintComputedFee)
                .as("the wallet must charge itself exactly what the mint charges it")
                .isEqualTo(plan.getFee());
    }

    private int feeTheMintWouldCharge(PostSwapRequest<LiteralSecret> request) {
        try {
            return request.getFees(mintSideResolver());
        } catch (Exception e) {
            throw new IllegalStateException("The mint could not price this swap", e);
        }
    }

    /**
     * The keysets as the mint holds them, built independently of the wallet's cache so that a
     * wallet agreeing only with itself does not pass.
     */
    private KeySetResolver mintSideResolver() {
        return KeySetResolver.of(Map.of(
                ACTIVE_KEYSET_ID, mintKeySet(ACTIVE_KEYSET_ID, publishedFeeOf(ACTIVE_KEYSET_ID)),
                RETIRED_KEYSET_ID, mintKeySet(RETIRED_KEYSET_ID, publishedFeeOf(RETIRED_KEYSET_ID))));
    }

    private int publishedFeeOf(String keysetId) {
        return keysetCache.findById(keysetId).map(ActiveKeySet::getInputFeePpk).orElse(0);
    }

    private static KeySet mintKeySet(String keysetId, int inputFeePpk) {
        return KeySet.builder().id(keysetId).unit(UNIT).partPerThousand(inputFeePpk).build();
    }

    private static List<BlindedMessage> blindedMessagesFor(SwapPlan<LiteralSecret> plan) {
        List<BlindedMessage> outputs = new ArrayList<>();
        for (int amount : plan.getOutputAmounts()) {
            outputs.add(BlindedMessage.builder()
                    .amount(amount)
                    .keySetId(KeysetId.fromString(plan.getOutputKeysetId()))
                    .blindedMessage(PrivateKey.derivePublicKey(
                            PrivateKey.fromString(String.format("%064x", BigInteger.valueOf(amount + 1L)))))
                    .build());
        }
        return outputs;
    }

    private void publishKeysets(ActiveKeySet... keysets) {
        keysetCache.refresh(List.of(keysets));
    }

    private static ActiveKeySet activeKeyset(int inputFeePpk) {
        return keyset(ACTIVE_KEYSET_ID, true, inputFeePpk);
    }

    private static ActiveKeySet retiredKeyset(int inputFeePpk) {
        return keyset(RETIRED_KEYSET_ID, false, inputFeePpk);
    }

    private static ActiveKeySet keyset(String id, boolean active, int inputFeePpk) {
        ActiveKeySet keyset = new ActiveKeySet();
        keyset.setId(id);
        keyset.setUnit(UNIT);
        keyset.setActive(active);
        keyset.setInputFeePpk(inputFeePpk);
        return keyset;
    }

    private static Proof<LiteralSecret> proof(int amount, String keysetId) {
        Proof<LiteralSecret> proof = new Proof<>();
        proof.setAmount(amount);
        proof.setKeySetId(keysetId);
        proof.setSecret(LiteralSecret.of("secret-" + keysetId + "-" + amount + "-" + System.nanoTime()));
        return proof;
    }
}
