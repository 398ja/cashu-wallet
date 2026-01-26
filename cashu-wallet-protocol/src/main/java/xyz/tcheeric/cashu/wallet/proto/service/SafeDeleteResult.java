package xyz.tcheeric.cashu.wallet.proto.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.Proof;

/**
 * Result of a safe proof deletion check via NUT-07.
 *
 * <p>This record represents the result of checking whether a proof can be safely deleted.
 * A proof should only be deleted if it has been confirmed as SPENT by the mint.
 * Deleting unspent proofs would result in permanent loss of funds.
 *
 * <p>The safe delete pattern:
 * <ol>
 *   <li>Check proof state via NUT-07 checkstate</li>
 *   <li>Only allow deletion if state is SPENT</li>
 *   <li>Be conservative on errors - don't delete if state cannot be confirmed</li>
 * </ol>
 *
 * <p>Usage example:
 * <pre>{@code
 * SafeDeleteResult result = service.canSafelyDelete(proof, mintUrl);
 * if (result.canDelete()) {
 *     proofRepository.delete(proof);
 * } else {
 *     log.warn("Cannot delete proof: {}", result.reason());
 * }
 * }</pre>
 *
 * @param proof the proof that was checked
 * @param state the state returned by the mint (SPENT, UNSPENT, PENDING, or null if error)
 * @param canDelete true if the proof can be safely deleted (state is SPENT)
 * @param reason human-readable reason for the decision
 * @param verificationSucceeded true if the state check completed without errors
 *
 * @see ProofRecoveryService#canSafelyDelete(Proof, String)
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/07.md">NUT-07: Token State Check</a>
 *
 * @author Security Implementation Team
 * @since 0.5.1
 */
public record SafeDeleteResult(
        @NonNull Proof<DeterministicSecret> proof,
        String state,
        boolean canDelete,
        @NonNull String reason,
        boolean verificationSucceeded
) {

    /**
     * Proof state indicating the proof has been spent.
     */
    public static final String STATE_SPENT = "SPENT";

    /**
     * Proof state indicating the proof is unspent and can still be used.
     */
    public static final String STATE_UNSPENT = "UNSPENT";

    /**
     * Proof state indicating the proof is in a pending state.
     */
    public static final String STATE_PENDING = "PENDING";

    /**
     * Creates a result indicating the proof can be safely deleted.
     *
     * @param proof the proof that was checked
     * @param state the state from the mint (should be SPENT)
     * @return result indicating deletion is safe
     */
    public static SafeDeleteResult canDelete(Proof<DeterministicSecret> proof, String state) {
        return new SafeDeleteResult(
                proof,
                state,
                true,
                "Proof confirmed as SPENT by mint, safe to delete",
                true
        );
    }

    /**
     * Creates a result indicating the proof should not be deleted because it's unspent.
     *
     * @param proof the proof that was checked
     * @param state the state from the mint (UNSPENT)
     * @return result indicating deletion is not safe
     */
    public static SafeDeleteResult cannotDeleteUnspent(Proof<DeterministicSecret> proof, String state) {
        return new SafeDeleteResult(
                proof,
                state,
                false,
                "Proof is UNSPENT - deleting would cause loss of funds",
                true
        );
    }

    /**
     * Creates a result indicating the proof should not be deleted because it's pending.
     *
     * @param proof the proof that was checked
     * @param state the state from the mint (PENDING)
     * @return result indicating deletion is not safe
     */
    public static SafeDeleteResult cannotDeletePending(Proof<DeterministicSecret> proof, String state) {
        return new SafeDeleteResult(
                proof,
                state,
                false,
                "Proof is PENDING - wait for state to settle before deletion",
                true
        );
    }

    /**
     * Creates a result indicating the proof should not be deleted due to unknown state.
     *
     * @param proof the proof that was checked
     * @param state the state from the mint (or null)
     * @return result indicating deletion is not safe
     */
    public static SafeDeleteResult cannotDeleteUnknownState(Proof<DeterministicSecret> proof, String state) {
        return new SafeDeleteResult(
                proof,
                state,
                false,
                "Proof state is unknown or unrecognized - preserving proof to prevent loss",
                true
        );
    }

    /**
     * Creates a result indicating the proof should not be deleted due to verification failure.
     *
     * @param proof the proof that was checked
     * @param error the exception that occurred
     * @return result indicating deletion is not safe due to error
     */
    public static SafeDeleteResult verificationFailed(Proof<DeterministicSecret> proof, Exception error) {
        return new SafeDeleteResult(
                proof,
                null,
                false,
                "State verification failed: " + error.getMessage() + " - preserving proof to prevent loss",
                false
        );
    }

    /**
     * Returns true if the proof state is confirmed as SPENT.
     *
     * @return true if state is SPENT
     */
    public boolean isSpent() {
        return state != null && isSpentState(state);
    }

    /**
     * Returns true if the proof state is confirmed as UNSPENT.
     *
     * @return true if state is UNSPENT
     */
    public boolean isUnspent() {
        return state != null && isUnspentState(state);
    }

    /**
     * Returns true if the proof state is PENDING.
     *
     * @return true if state is PENDING
     */
    public boolean isPending() {
        return state != null && isPendingState(state);
    }

    /**
     * Checks if the given state indicates the proof is spent.
     * Handles both string and numeric representations.
     */
    private static boolean isSpentState(String state) {
        String normalized = state.trim().toUpperCase();
        return STATE_SPENT.equals(normalized) || "1".equals(normalized);
    }

    /**
     * Checks if the given state indicates the proof is unspent.
     * Handles both string and numeric representations.
     */
    private static boolean isUnspentState(String state) {
        String normalized = state.trim().toUpperCase();
        return STATE_UNSPENT.equals(normalized) || "0".equals(normalized);
    }

    /**
     * Checks if the given state indicates the proof is pending.
     * Handles both string and numeric representations.
     */
    private static boolean isPendingState(String state) {
        String normalized = state.trim().toUpperCase();
        return STATE_PENDING.equals(normalized) || "2".equals(normalized);
    }
}
