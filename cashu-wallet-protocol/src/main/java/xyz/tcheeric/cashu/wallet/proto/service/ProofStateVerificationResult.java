package xyz.tcheeric.cashu.wallet.proto.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.Proof;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of verifying proof states via NUT-07 checkstate.
 *
 * <p>This record provides detailed information about proof states after verification,
 * including which proofs are unspent, which are spent, and any proofs whose state
 * could not be determined (due to network errors or missing responses).
 *
 * <p>Usage example:
 * <pre>{@code
 * ProofStateVerificationResult result = service.verifyProofsUnspent(proofs, mintUrl);
 * if (result.allUnspent()) {
 *     // Safe to proceed with all proofs
 * } else {
 *     // Handle spent proofs
 *     log.warn("Found {} spent proofs", result.spentProofs().size());
 * }
 * }</pre>
 *
 * @param unspentProofs proofs confirmed as unspent by the mint
 * @param spentProofs proofs confirmed as spent by the mint
 * @param unknownProofs proofs whose state could not be determined
 * @param stateBySecret map of proof secret hash to state string for debugging
 * @param success true if the verification completed without errors
 * @param errorMessage error message if verification failed, null otherwise
 *
 * @see ProofRecoveryService#verifyProofsUnspent(List, String)
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/07.md">NUT-07: Token State Check</a>
 *
 * @author Security Implementation Team
 * @since 0.5.1
 */
public record ProofStateVerificationResult(
        @NonNull List<Proof<DeterministicSecret>> unspentProofs,
        @NonNull List<Proof<DeterministicSecret>> spentProofs,
        @NonNull List<Proof<DeterministicSecret>> unknownProofs,
        @NonNull Map<String, String> stateBySecret,
        boolean success,
        String errorMessage
) {

    /**
     * Creates a successful verification result.
     *
     * @param unspentProofs proofs confirmed as unspent
     * @param spentProofs proofs confirmed as spent
     * @param unknownProofs proofs with unknown state
     * @param stateBySecret map of secret hash to state
     * @return successful verification result
     */
    public static ProofStateVerificationResult success(
            List<Proof<DeterministicSecret>> unspentProofs,
            List<Proof<DeterministicSecret>> spentProofs,
            List<Proof<DeterministicSecret>> unknownProofs,
            Map<String, String> stateBySecret
    ) {
        return new ProofStateVerificationResult(
                List.copyOf(unspentProofs),
                List.copyOf(spentProofs),
                List.copyOf(unknownProofs),
                Map.copyOf(stateBySecret),
                true,
                null
        );
    }

    /**
     * Creates a failed verification result due to an error.
     *
     * @param error the exception that caused the failure
     * @param inputProofs the original proofs that were being verified
     * @return failed verification result with all proofs marked as unknown
     */
    public static ProofStateVerificationResult failure(Exception error, List<Proof<DeterministicSecret>> inputProofs) {
        return new ProofStateVerificationResult(
                Collections.emptyList(),
                Collections.emptyList(),
                List.copyOf(inputProofs),
                Collections.emptyMap(),
                false,
                error.getMessage()
        );
    }

    /**
     * Creates an empty result for when no proofs were provided.
     *
     * @return empty successful verification result
     */
    public static ProofStateVerificationResult empty() {
        return new ProofStateVerificationResult(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                true,
                null
        );
    }

    /**
     * Returns true if all input proofs were verified as unspent.
     *
     * @return true if no proofs are spent or unknown
     */
    public boolean allUnspent() {
        return success && spentProofs.isEmpty() && unknownProofs.isEmpty();
    }

    /**
     * Returns true if any proofs were verified as spent.
     *
     * @return true if at least one proof is spent
     */
    public boolean hasSpentProofs() {
        return !spentProofs.isEmpty();
    }

    /**
     * Returns true if any proofs have unknown state.
     *
     * @return true if at least one proof state is unknown
     */
    public boolean hasUnknownProofs() {
        return !unknownProofs.isEmpty();
    }

    /**
     * Returns the total number of proofs that were verified.
     *
     * @return total count of all proofs
     */
    public int totalProofCount() {
        return unspentProofs.size() + spentProofs.size() + unknownProofs.size();
    }

    /**
     * Returns the total amount of unspent proofs.
     *
     * @return sum of amounts of unspent proofs
     */
    public long unspentAmount() {
        return unspentProofs.stream()
                .mapToLong(Proof::getAmount)
                .sum();
    }

    /**
     * Returns the total amount of spent proofs.
     *
     * @return sum of amounts of spent proofs
     */
    public long spentAmount() {
        return spentProofs.stream()
                .mapToLong(Proof::getAmount)
                .sum();
    }
}
