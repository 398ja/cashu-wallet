package xyz.tcheeric.cashu.wallet.proto.service;

/**
 * Result of a NUT-12 DLEQ verification attempt.
 *
 * <p>A boolean cannot express the difference between "the mint proved this signature"
 * and "the mint sent no proof at all". Callers that need that distinction, for example
 * to warn a user that a token is unproven, read this outcome instead.
 *
 * <p>A failed verification never produces an outcome: it raises
 * {@link DLEQVerificationException}.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/12.md">NUT-12: DLEQ proofs</a>
 */
public enum DLEQVerificationOutcome {

    /** A DLEQ proof was present and verified against the mint public key. */
    VERIFIED,

    /** No DLEQ proof was attached, and the mint does not advertise NUT-12 support. */
    NO_PROOF_PRESENT;

    /** Returns true when the mint cryptographically proved the signature. */
    public boolean isVerified() {
        return this == VERIFIED;
    }

    /** Returns true when the subject carried no DLEQ data to verify. */
    public boolean isProofMissing() {
        return this == NO_PROOF_PRESENT;
    }
}
