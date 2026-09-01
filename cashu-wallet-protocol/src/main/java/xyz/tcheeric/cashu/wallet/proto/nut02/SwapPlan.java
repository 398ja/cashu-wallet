package xyz.tcheeric.cashu.wallet.proto.nut02;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;

import java.util.List;

/**
 * The arithmetic of one NUT-02 transaction: which proofs are spent, what fee they incur, and the
 * output amounts that remain once the fee is subtracted.
 *
 * <p>The mint checks {@code sum(inputs) - fees == sum(outputs)} and refuses anything else, so
 * this is the shape a wallet must get exactly right. Holding the three quantities together, with
 * the keyset the outputs name, makes the balance assertable before a request is ever sent.
 *
 * @param <T> the secret type carried by the inputs
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 */
@Getter
@Builder
@ToString
public class SwapPlan<T extends Secret> {

    /** The proofs to spend, in the order they must be sent. */
    @NonNull
    private final List<Proof<T>> inputs;

    /** The output denominations, summing to {@code inputTotal - fee}. */
    @NonNull
    private final List<Integer> outputAmounts;

    /** The NUT-02 fee these inputs incur, already subtracted from the output amounts. */
    private final int fee;

    /** The active keyset the outputs are issued against. */
    @NonNull
    private final String outputKeysetId;

    /** Returns the total value of the inputs. */
    public int getInputTotal() {
        return inputs.stream().mapToInt(Proof::getAmount).sum();
    }

    /** Returns the total value of the outputs. */
    public int getOutputTotal() {
        return outputAmounts.stream().mapToInt(Integer::intValue).sum();
    }

    /** Answers whether this plan satisfies the equation the mint enforces. */
    public boolean isBalanced() {
        return getInputTotal() - fee == getOutputTotal();
    }
}
