package xyz.tcheeric.cashu.wallet.proto.nut02;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.common.LiteralSecret;
import xyz.tcheeric.cashu.common.Proof;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InactiveFirstProofSelector}, which implements the NUT-02 SHOULD that a
 * wallet drain its inactive keysets rather than strand value on them.
 */
class InactiveFirstProofSelectorTest {

    private static final String ACTIVE_KEYSET_ID = "004cf8cba2f93266";
    private static final String RETIRED_KEYSET_ID = "009a1f293253e41e";

    private KeysetCache cache;
    private InactiveFirstProofSelector selector;

    @BeforeEach
    void setUp() {
        cache = new KeysetCache();
        cache.refresh(List.of(
                keyset(ACTIVE_KEYSET_ID, true, 0),
                keyset(RETIRED_KEYSET_ID, false, 0)));
        selector = new InactiveFirstProofSelector(cache);
    }

    /**
     * Ensures proofs of an inactive keyset are spent before active ones, even when the active
     * proof is larger. A mint never issues against a retired keyset again, so ordinary traffic is
     * the only thing that ever moves that balance.
     */
    @Test
    void shouldSpendInactiveKeysetProofsFirst() {
        List<Proof<LiteralSecret>> prioritised = selector.prioritise(List.of(
                proof(16, ACTIVE_KEYSET_ID),
                proof(2, RETIRED_KEYSET_ID)));

        assertThat(prioritised).extracting(Proof::getKeySetId)
                .containsExactly(RETIRED_KEYSET_ID, ACTIVE_KEYSET_ID);
    }

    /**
     * Ensures the largest proof of a group is taken first, so a target is met with as few inputs
     * as possible. NUT-02 charges per input, so fewer inputs is also a smaller fee.
     */
    @Test
    void shouldTakeTheLargestProofFirstWithinAKeyset() {
        List<Proof<LiteralSecret>> prioritised = selector.prioritise(List.of(
                proof(1, ACTIVE_KEYSET_ID),
                proof(8, ACTIVE_KEYSET_ID),
                proof(4, ACTIVE_KEYSET_ID)));

        assertThat(prioritised).extracting(Proof::getAmount).containsExactly(8, 4, 1);
    }

    /**
     * Ensures selection stops as soon as the target is covered, rather than spending the whole
     * wallet on a small payment.
     */
    @Test
    void shouldSelectOnlyAsManyProofsAsTheTargetNeeds() {
        List<Proof<LiteralSecret>> selected = selector.select(List.of(
                proof(8, ACTIVE_KEYSET_ID),
                proof(8, ACTIVE_KEYSET_ID),
                proof(8, ACTIVE_KEYSET_ID)), 8);

        assertThat(selected).hasSize(1);
    }

    /**
     * Ensures selection covers the fee as well as the target. Selecting a proof to pay the fee
     * raises the fee again, so a wallet that budgets for the fee once ends up one input short and
     * hands the mint an unbalanced transaction.
     */
    @Test
    void shouldSelectEnoughToCoverTheFeeAsWellAsTheTarget() {
        cache.refresh(List.of(keyset(ACTIVE_KEYSET_ID, true, 1000)));

        List<Proof<LiteralSecret>> selected = selector.select(List.of(
                proof(8, ACTIVE_KEYSET_ID),
                proof(4, ACTIVE_KEYSET_ID),
                proof(1, ACTIVE_KEYSET_ID)), 8);

        int selectedTotal = selected.stream().mapToInt(Proof::getAmount).sum();
        int fee = new WalletFeeCalculator(cache).feeFor(selected);
        assertThat(selected)
                .as("one 8-sat proof covers the target but not the satoshi it costs to spend")
                .hasSize(2);
        assertThat(selectedTotal - fee).isGreaterThanOrEqualTo(8);
    }

    /**
     * Ensures a target the wallet cannot afford fails loudly, naming what is available, rather
     * than silently returning a short selection the mint would reject.
     */
    @Test
    void shouldFailWhenTheProofsCannotCoverTheTarget() {
        assertThatThrownBy(() -> selector.select(List.of(proof(2, ACTIVE_KEYSET_ID)), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cover 10");
    }

    private static ActiveKeySet keyset(String id, boolean active, int inputFeePpk) {
        ActiveKeySet keyset = new ActiveKeySet();
        keyset.setId(id);
        keyset.setUnit("sat");
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
