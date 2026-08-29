package xyz.tcheeric.cashu.wallet.proto.nut02;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses which proofs to spend, preferring those issued by inactive keysets.
 *
 * <p>NUT-02 keeps the proofs of an inactive keyset spendable but never issues new ones against
 * it, so value left there is only ever moved by a wallet that deliberately spends it. Spending
 * those proofs first drains a rotated keyset over ordinary traffic instead of stranding the
 * balance on it until a dedicated consolidation run that may never happen.
 *
 * <p>Within each group proofs are taken largest first, so a target is met with as few inputs as
 * possible. That also minimises the fee, which NUT-02 charges per input rather than per amount.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 */
@Nut(2)
@Slf4j
@RequiredArgsConstructor
public class InactiveFirstProofSelector {

    @NonNull
    private final KeysetCache keysetCache;

    /**
     * Orders proofs so that inactive-keyset proofs come first, then largest amount first.
     *
     * @param proofs the proofs available to spend
     * @return a new list; the input list is not modified
     */
    public <T extends Secret> List<Proof<T>> prioritise(@NonNull List<Proof<T>> proofs) {
        List<Proof<T>> prioritised = new ArrayList<>(proofs);
        prioritised.sort(Comparator
                .comparing((Proof<T> proof) -> keysetCache.isActive(proof.getKeySetId()))
                .thenComparing(Comparator.comparingInt(Proof<T>::getAmount).reversed()));
        return prioritised;
    }

    /**
     * Selects enough proofs to cover {@code targetAmount} plus the fee their own selection
     * incurs, preferring inactive-keyset proofs.
     *
     * <p>The fee depends on how many inputs are chosen, and choosing another input to pay the
     * fee can itself raise the fee, so the requirement is re-evaluated after every addition
     * rather than computed once up front.
     *
     * @param proofs the proofs available to spend
     * @param targetAmount the amount that must remain after fees
     * @return the chosen proofs, in the order they should be sent
     * @throws IllegalStateException when the available proofs cannot cover the target plus fees
     */
    public <T extends Secret> List<Proof<T>> select(@NonNull List<Proof<T>> proofs, int targetAmount) {
        requireNonNegative(targetAmount);

        List<Proof<T>> selected = new ArrayList<>();
        int selectedTotal = 0;
        for (Proof<T> candidate : prioritise(proofs)) {
            if (coversTargetWithFees(selected, selectedTotal, targetAmount)) {
                break;
            }
            selected.add(candidate);
            selectedTotal += candidate.getAmount();
        }

        if (!coversTargetWithFees(selected, selectedTotal, targetAmount)) {
            throw new IllegalStateException(
                    "Cannot cover " + targetAmount + " plus NUT-02 fees from the available proofs."
                            + " Available totals " + totalOf(proofs) + " across " + proofs.size() + " proofs."
                            + " Suggestion: mint or receive more proofs before spending.");
        }

        log.info("proof_selection completed target={} selected={} total={}",
                targetAmount, selected.size(), selectedTotal);
        return selected;
    }

    private <T extends Secret> boolean coversTargetWithFees(
            List<Proof<T>> selected, int selectedTotal, int targetAmount) {

        if (selected.isEmpty()) {
            return targetAmount == 0;
        }
        return selectedTotal - new WalletFeeCalculator(keysetCache).feeFor(selected) >= targetAmount;
    }

    private static <T extends Secret> int totalOf(List<Proof<T>> proofs) {
        return proofs.stream().mapToInt(Proof::getAmount).sum();
    }

    private static void requireNonNegative(int targetAmount) {
        if (targetAmount < 0) {
            throw new IllegalArgumentException("Target amount cannot be negative, got: " + targetAmount);
        }
    }
}
