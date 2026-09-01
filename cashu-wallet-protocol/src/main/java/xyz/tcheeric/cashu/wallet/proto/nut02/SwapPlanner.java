package xyz.tcheeric.cashu.wallet.proto.nut02;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.util.SplittingService;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.List;

/**
 * Plans a NUT-02 compliant swap: fee subtracted from the outputs, outputs issued only against an
 * active keyset, inputs drawn from inactive keysets first.
 *
 * <p>A wallet that splits its inputs evenly into outputs is building a transaction a fee-charging
 * mint refuses, because the mint checks {@code sum(inputs) - fees == sum(outputs)}. The fee is
 * therefore computed from the chosen inputs, using the keyset each of them was issued under, and
 * taken off the output total before it is split into denominations.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/03.md">NUT-03: Swap</a>
 */
@Nut(2)
@Slf4j
public class SwapPlanner {

    private final KeysetCache keysetCache;
    private final WalletFeeCalculator feeCalculator;
    private final InactiveFirstProofSelector proofSelector;
    private final SplittingService splittingService;

    /** Creates a planner over a keyset cache, with the default fee, selection and split policy. */
    public SwapPlanner(@NonNull KeysetCache keysetCache) {
        this(keysetCache,
                new WalletFeeCalculator(keysetCache),
                new InactiveFirstProofSelector(keysetCache),
                new SplittingService());
    }

    public SwapPlanner(
            @NonNull KeysetCache keysetCache,
            @NonNull WalletFeeCalculator feeCalculator,
            @NonNull InactiveFirstProofSelector proofSelector,
            @NonNull SplittingService splittingService) {
        this.keysetCache = keysetCache;
        this.feeCalculator = feeCalculator;
        this.proofSelector = proofSelector;
        this.splittingService = splittingService;
    }

    /**
     * Plans a swap of the given proofs into fresh outputs of the same total, less fees.
     *
     * @param proofs the proofs to spend, spent in inactive-keyset-first order
     * @param unit the keyset unit the outputs are denominated in
     * @param availableDenominations the denominations the active keyset publishes
     * @return a balanced plan whose outputs the mint will accept
     * @throws IllegalStateException when no active keyset serves the unit, or when the fee would
     *     consume the whole input, which would leave the mint nothing to sign
     */
    public <T extends Secret> SwapPlan<T> planSwap(
            @NonNull List<Proof<T>> proofs,
            @NonNull String unit,
            @NonNull List<Integer> availableDenominations) {

        requireInputs(proofs);

        List<Proof<T>> inputs = proofSelector.prioritise(proofs);
        int inputTotal = inputs.stream().mapToInt(Proof::getAmount).sum();
        int fee = feeCalculator.feeFor(inputs);
        int outputTotal = requireSomethingLeftAfterFees(inputTotal, fee);

        SwapPlan<T> plan = SwapPlan.<T>builder()
                .inputs(inputs)
                .outputAmounts(splittingService.split(outputTotal, availableDenominations))
                .fee(fee)
                .outputKeysetId(activeKeysetFor(unit).getId())
                .build();

        log.info("swap_planned inputs={} input_total={} fee={} output_total={} output_keyset={}",
                inputs.size(), inputTotal, fee, plan.getOutputTotal(), plan.getOutputKeysetId());
        return plan;
    }

    /**
     * Returns the active keyset the wallet must build outputs against for this unit.
     *
     * <p>NUT-02 makes this a MUST, and a mint refuses outputs on an inactive keyset outright, so
     * an absent active keyset is a hard failure rather than a fallback to whatever was cached.
     *
     * @throws IllegalStateException when the mint publishes no active keyset for the unit
     */
    public ActiveKeySet activeKeysetFor(@NonNull String unit) {
        List<ActiveKeySet> active = keysetCache.activeKeysets(unit);
        if (active.isEmpty()) {
            throw new IllegalStateException(
                    "No active keyset for unit " + unit + ". Outputs may only be built against an"
                            + " active keyset, and the mint publishes none."
                            + " Suggestion: refresh the keyset cache from GET /v1/keysets.");
        }
        return active.get(0);
    }

    private static int requireSomethingLeftAfterFees(int inputTotal, int fee) {
        int outputTotal = inputTotal - fee;
        if (outputTotal <= 0) {
            throw new IllegalStateException(
                    "The NUT-02 fee of " + fee + " consumes the whole input of " + inputTotal
                            + ". A swap with no outputs leaves the mint nothing to sign."
                            + " Suggestion: spend fewer, larger proofs or wait for a cheaper keyset.");
        }
        return outputTotal;
    }

    private static <T extends Secret> void requireInputs(List<Proof<T>> proofs) {
        if (proofs.isEmpty()) {
            throw new IllegalArgumentException("Swap inputs cannot be empty");
        }
    }
}
