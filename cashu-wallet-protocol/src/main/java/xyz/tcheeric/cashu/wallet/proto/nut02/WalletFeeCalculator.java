package xyz.tcheeric.cashu.wallet.proto.nut02;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.nut02.InputFeeCalculator;
import xyz.tcheeric.cashu.common.nut02.UnknownKeySetException;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.List;

/**
 * The NUT-02 input fee a wallet owes for spending a set of proofs.
 *
 * <p>The arithmetic itself is {@code cashu-lib}'s {@link InputFeeCalculator}, the same class the
 * mint prices a swap with. Sharing it is the point: a wallet that reimplements the rounding rule
 * is a wallet whose transactions the mint may refuse, or, worse, one that overpays silently.
 *
 * <p>What this class adds is the wallet's failure mode. An input naming a keyset the wallet has
 * not cached cannot be priced, and a fee guessed at zero produces an unbalanced transaction, so
 * the attempt fails loudly instead.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 */
@Nut(2)
@RequiredArgsConstructor
public class WalletFeeCalculator {

    @NonNull
    private final KeysetCache keysetCache;

    /**
     * Returns the fee owed for spending these proofs, in the keyset unit.
     *
     * @throws IllegalStateException when a proof names a keyset the cache does not know
     */
    public int feeFor(@NonNull List<? extends Proof<? extends Secret>> inputs) {
        try {
            return new InputFeeCalculator(keysetCache.asKeySetResolver()).calculateFee(inputs);
        } catch (UnknownKeySetException e) {
            throw new IllegalStateException(
                    "Cannot compute the NUT-02 fee for keyset " + e.getKeySetId()
                            + ". The wallet's keyset cache does not know it, and assuming a zero fee"
                            + " would build a transaction the mint refuses as unbalanced."
                            + " Suggestion: refresh the keyset cache from GET /v1/keysets and retry.", e);
        }
    }
}
