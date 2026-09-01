package xyz.tcheeric.cashu.wallet.proto.service;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.MintInformation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DLEQPolicy}, which decides whether a missing NUT-12 proof is fatal.
 */
class DLEQPolicyTest {

    /**
     * Ensures a mint that advertises NUT-12 support makes DLEQ proofs mandatory.
     */
    @Test
    void shouldRequireProofWhenMintAdvertisesNut12() {
        MintInformation mintInformation = mintInformationWithNut12(Boolean.TRUE);

        assertThat(DLEQPolicy.forMint(mintInformation)).isEqualTo(DLEQPolicy.REQUIRED);
        assertThat(DLEQPolicy.forMint(mintInformation).requiresProof()).isTrue();
    }

    /**
     * Ensures a mint that explicitly reports no NUT-12 support leaves proofs optional.
     */
    @Test
    void shouldNotRequireProofWhenMintReportsNut12Unsupported() {
        assertThat(DLEQPolicy.forMint(mintInformationWithNut12(Boolean.FALSE)))
            .isEqualTo(DLEQPolicy.OPTIONAL);
    }

    /**
     * Ensures a mint whose information omits NUT-12 leaves proofs optional.
     */
    @Test
    void shouldNotRequireProofWhenMintOmitsNut12() {
        assertThat(DLEQPolicy.forMint(new MintInformation())).isEqualTo(DLEQPolicy.OPTIONAL);
    }

    /**
     * Ensures an unavailable mint information document does not accidentally enforce NUT-12.
     */
    @Test
    void shouldNotRequireProofWhenMintInformationIsUnavailable() {
        assertThat(DLEQPolicy.forMint(null)).isEqualTo(DLEQPolicy.OPTIONAL);
    }

    private MintInformation mintInformationWithNut12(Boolean supported) {
        MintInformation mintInformation = new MintInformation();
        MintInformation.NutConfig nut12 = new MintInformation.NutConfig();
        nut12.setSupported(supported);
        mintInformation.put("12", nut12);
        return mintInformation;
    }
}
