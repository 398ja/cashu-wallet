package xyz.tcheeric.cashu.wallet.proto.nut02;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.ActiveKeySet;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link KeysetCache}, the wallet's view of which keysets are active and what
 * each one charges.
 */
class KeysetCacheTest {

    private static final String FIRST_KEYSET_ID = "004cf8cba2f93266";
    private static final String SECOND_KEYSET_ID = "009a1f293253e41e";

    private KeysetCache cache;

    @BeforeEach
    void setUp() {
        cache = new KeysetCache();
    }

    /**
     * Ensures the cache reports the fee and the active flag a mint published, since both are read
     * straight back out when a transaction is built.
     */
    @Test
    void shouldReportTheFeeAndActiveFlagTheMintPublished() {
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, true, 1000)));

        assertThat(cache.isActive(FIRST_KEYSET_ID)).isTrue();
        assertThat(cache.inputFeePpk(FIRST_KEYSET_ID)).isEqualTo(1000);
    }

    /**
     * Ensures a refresh picks up an active flag that flipped to false. A rotation is expressed
     * only by that flag, and a cache that kept the old value would keep building outputs the mint
     * now refuses.
     */
    @Test
    void shouldPickUpAnActiveFlagFlipOnRefresh() {
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, true, 0)));
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, false, 0), keyset(SECOND_KEYSET_ID, true, 0)));

        assertThat(cache.isActive(FIRST_KEYSET_ID)).isFalse();
        assertThat(cache.activeKeysets("sat")).extracting(ActiveKeySet::getId)
                .containsExactly(SECOND_KEYSET_ID);
    }

    /**
     * Ensures a keyset the mint has withdrawn does not survive a refresh, so the wallet never
     * addresses outputs to a keyset that is no longer listed.
     */
    @Test
    void shouldDropAKeysetTheMintNoLongerPublishes() {
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, true, 0), keyset(SECOND_KEYSET_ID, true, 0)));
        cache.refresh(List.of(keyset(SECOND_KEYSET_ID, true, 0)));

        assertThat(cache.findById(FIRST_KEYSET_ID)).isEmpty();
    }

    /**
     * Ensures an unknown keyset is treated as inactive rather than assumed usable, because a
     * spec-conforming mint refuses outputs against anything but an active keyset.
     */
    @Test
    void shouldTreatAnUnknownKeysetAsInactive() {
        assertThat(cache.isActive(FIRST_KEYSET_ID)).isFalse();
    }

    /**
     * Ensures pricing an unknown keyset fails rather than returning zero. A guessed fee of zero
     * builds a transaction the mint rejects as unbalanced, with no hint of the real cause.
     */
    @Test
    void shouldRefuseToPriceAnUnknownKeyset() {
        assertThatThrownBy(() -> cache.inputFeePpk(FIRST_KEYSET_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(FIRST_KEYSET_ID);
    }

    /**
     * Ensures active keysets are filtered by unit, since a mint may serve several and outputs of
     * one unit cannot be issued against a keyset denominated in another.
     */
    @Test
    void shouldReturnOnlyActiveKeysetsOfTheRequestedUnit() {
        ActiveKeySet otherUnit = keyset(SECOND_KEYSET_ID, true, 0);
        otherUnit.setUnit("usd");
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, true, 0), otherUnit));

        assertThat(cache.activeKeysets("sat")).extracting(ActiveKeySet::getId)
                .containsExactly(FIRST_KEYSET_ID);
    }

    /**
     * Ensures the resolver handed to cashu-lib carries the published fee, since that is the only
     * field fee resolution reads and the whole reason the cache exists.
     */
    @Test
    void shouldExposeThePublishedFeeThroughTheResolver() {
        cache.refresh(List.of(keyset(FIRST_KEYSET_ID, true, 250)));

        assertThat(cache.asKeySetResolver().findById(FIRST_KEYSET_ID))
                .hasValueSatisfying(keySet -> assertThat(keySet.getPartPerThousand()).isEqualTo(250));
    }

    /**
     * Ensures a listing entry without an id is rejected, since it cannot be matched to any proof
     * and would silently disappear from the cache.
     */
    @Test
    void shouldRejectAKeysetWithoutAnId() {
        assertThatThrownBy(() -> cache.refresh(List.of(keyset(null, true, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must carry an id");
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
