package xyz.tcheeric.cashu.wallet.client.service;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of a melt, including the NUT-08 change the mint returned for unspent fee reserve.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/08.md">NUT-08: Lightning fee return</a>
 */
@Getter
@Builder
@ToString(exclude = "changeProofs")
public class MeltResult {

    /** Whether the mint reported the Lightning payment as paid. */
    private final boolean paid;

    /** Lightning payment preimage, present once the payment settled. */
    private final String paymentPreimage;

    /** Proofs unblinded from the returned change; empty when the mint returned none. */
    private final List<Proof<DeterministicSecret>> changeProofs;

    /** Counter to derive from next, advanced past every blank output that was sent. */
    private final int nextCounter;

    /** Returns an unmodifiable view of the recovered change proofs. */
    public List<Proof<DeterministicSecret>> getChangeProofs() {
        return changeProofs == null ? List.of() : Collections.unmodifiableList(changeProofs);
    }

    /** Returns the total value recovered from the unspent fee reserve. */
    public int getChangeAmount() {
        return getChangeProofs().stream().mapToInt(Proof::getAmount).sum();
    }
}
