package xyz.tcheeric.cashu.wallet.proto.mnemonic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A seed phrase should not outlive the derivation it was needed for.
 *
 * <p>{@code loadFromMnemonic} takes a String, and a String cannot be cleared: it is immutable,
 * it may be interned, and the caller holds a reference the callee cannot reach. The phrase
 * therefore stayed resident until the collector happened to reclaim it (audit M-18, and SW-04
 * before it, which was marked fixed after zeroing only the derived blinding factors).
 */
@DisplayName("A mnemonic is cleared once the master key is derived")
class MnemonicSeedTest {

    /** A valid BIP39 test vector. */
    private static final String VALID_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon "
                    + "abandon abandon abandon abandon abandon about";

    @Test
    @DisplayName("the phrase is zeroed after deriving")
    void phraseIsZeroedAfterDeriving() {
        char[] phrase = VALID_MNEMONIC.toCharArray();
        MnemonicSeed seed = MnemonicSeed.of(phrase);

        seed.deriveMasterKey("");

        assertThat(phrase)
                .as("the caller's array is the one thing we can actually clear, so it must be")
                .containsOnly('\0');
        assertThat(seed.isCleared()).isTrue();
    }

    @Test
    @DisplayName("deriving still produces the right key")
    void derivesTheSameKeyAsTheStringApi() {
        char[] phrase = VALID_MNEMONIC.toCharArray();

        var viaSeed = MnemonicSeed.of(phrase).deriveMasterKey("");
        var viaString = MnemonicManager.loadFromMnemonic(VALID_MNEMONIC, "");

        assertThat(viaSeed.serializePrivB58(org.bitcoinj.base.BitcoinNetwork.MAINNET))
                .as("clearing the input must not change the derivation")
                .isEqualTo(viaString.serializePrivB58(org.bitcoinj.base.BitcoinNetwork.MAINNET));
    }

    @Test
    @DisplayName("close() clears without deriving")
    void closeClearsWithoutDeriving() {
        char[] phrase = VALID_MNEMONIC.toCharArray();

        try (MnemonicSeed seed = MnemonicSeed.of(phrase)) {
            assertThat(seed.isCleared()).isFalse();
        }

        assertThat(phrase).containsOnly('\0');
    }

    @Test
    @DisplayName("a cleared seed cannot be used again")
    void clearedSeedCannotDerive() {
        MnemonicSeed seed = MnemonicSeed.of(VALID_MNEMONIC.toCharArray());
        seed.close();

        assertThatThrownBy(() -> seed.deriveMasterKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cleared");
    }

    @Test
    @DisplayName("clearing twice is harmless")
    void closeIsIdempotent() {
        char[] phrase = VALID_MNEMONIC.toCharArray();
        MnemonicSeed seed = MnemonicSeed.of(phrase);

        seed.deriveMasterKey("");
        seed.close();

        assertThat(phrase).containsOnly('\0');
    }

    @Test
    @DisplayName("an invalid mnemonic still clears the phrase")
    void invalidMnemonicStillClears() {
        char[] phrase = "not a valid mnemonic at all".toCharArray();
        MnemonicSeed seed = MnemonicSeed.of(phrase);

        assertThatThrownBy(() -> seed.deriveMasterKey(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(phrase)
                .as("a rejected phrase is still a phrase, and must not be left in memory")
                .containsOnly('\0');
    }
}
