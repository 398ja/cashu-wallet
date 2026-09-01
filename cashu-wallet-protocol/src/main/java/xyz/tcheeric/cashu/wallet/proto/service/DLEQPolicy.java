package xyz.tcheeric.cashu.wallet.proto.service;

import xyz.tcheeric.cashu.common.MintInformation;

import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a missing NUT-12 DLEQ proof is tolerable.
 *
 * <p>NUT-12 is optional for a mint, but once a mint advertises support in its NUT-06
 * information, a signature without a proof is a protocol violation and must fail rather
 * than be silently accepted as verified.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/12.md">NUT-12: DLEQ proofs</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/06.md">NUT-06: Mint information</a>
 */
public enum DLEQPolicy {

    /** The mint advertises NUT-12; every signature and proof must carry DLEQ data. */
    REQUIRED,

    /** The mint does not advertise NUT-12; a missing proof is accepted unverified. */
    OPTIONAL;

    private static final String NUT12_KEY = "12";

    /**
     * Derives the policy from the mint's NUT-06 information document.
     *
     * @param mintInformation the mint information, may be null when unavailable
     * @return {@link #REQUIRED} when the mint advertises NUT-12 support, otherwise {@link #OPTIONAL}
     */
    public static DLEQPolicy forMint(MintInformation mintInformation) {
        return advertisesNut12(mintInformation) ? REQUIRED : OPTIONAL;
    }

    /** Returns true when a missing DLEQ proof must be rejected. */
    public boolean requiresProof() {
        return this == REQUIRED;
    }

    private static boolean advertisesNut12(MintInformation mintInformation) {
        return Optional.ofNullable(mintInformation)
            .map(MintInformation::getNuts)
            .map(nuts -> supportedNut12(nuts))
            .orElse(false);
    }

    private static boolean supportedNut12(Map<String, MintInformation.NutConfig> nuts) {
        MintInformation.NutConfig nut12 = nuts.get(NUT12_KEY);
        return nut12 != null && Boolean.TRUE.equals(nut12.getSupported());
    }
}
