package xyz.tcheeric.cashu.wallet.client.service;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.entities.rest.GetActiveKeySetsResponse;
import xyz.tcheeric.cashu.wallet.proto.nut02.KeysetCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link KeysetDirectory}, which keeps the wallet's keyset cache in step with a
 * mint's {@code GET /v1/keysets} listing.
 */
class KeysetDirectoryTest {

    private static final String MINT_URL = "https://mint.example.com";
    private static final String KEYSET_ID = "004cf8cba2f93266";

    /**
     * Ensures a refresh loads the fee and active flag from the mint's listing into the cache,
     * which is the only way either value reaches the wallet's transaction arithmetic.
     */
    @Test
    void shouldLoadTheMintsPublishedFeeIntoTheCache() {
        KeysetCache cache = new KeysetCache();
        KeysetDirectory directory = new KeysetDirectory(MINT_URL, cache,
                mintUrl -> listingOf(keyset(KEYSET_ID, true, 1000)));

        directory.refresh();

        assertThat(cache.isActive(KEYSET_ID)).isTrue();
        assertThat(cache.inputFeePpk(KEYSET_ID)).isEqualTo(1000);
    }

    /**
     * Ensures a second refresh picks up an active flag the operator flipped, so a rotated keyset
     * stops being chosen for outputs the mint would refuse to sign.
     */
    @Test
    void shouldPickUpARotationOnASecondRefresh() {
        KeysetCache cache = new KeysetCache();
        boolean[] rotated = {false};
        KeysetDirectory directory = new KeysetDirectory(MINT_URL, cache,
                mintUrl -> listingOf(keyset(KEYSET_ID, !rotated[0], 0)));

        directory.refresh();
        rotated[0] = true;
        directory.refresh();

        assertThat(cache.isActive(KEYSET_ID)).isFalse();
    }

    /**
     * Ensures an empty listing fails loudly. Silently caching nothing would surface later as an
     * unpriceable input or an absent active keyset, far from the real cause.
     */
    @Test
    void shouldFailWhenTheMintPublishesNoKeysets() {
        KeysetDirectory directory = new KeysetDirectory(MINT_URL, new KeysetCache(),
                mintUrl -> new GetActiveKeySetsResponse(List.of()));

        assertThatThrownBy(directory::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published no keysets");
    }

    private static GetActiveKeySetsResponse listingOf(ActiveKeySet... keysets) {
        return new GetActiveKeySetsResponse(List.of(keysets));
    }

    private static ActiveKeySet keyset(String id, boolean active, int inputFeePpk) {
        ActiveKeySet keyset = new ActiveKeySet();
        keyset.setId(id);
        keyset.setUnit("sat");
        keyset.setActive(active);
        keyset.setInputFeePpk(inputFeePpk);
        return keyset;
    }
}
