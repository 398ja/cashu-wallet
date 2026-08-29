package xyz.tcheeric.cashu.wallet.client.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.GetActiveKeySetsResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestActiveKeysets;
import xyz.tcheeric.cashu.wallet.proto.nut02.KeysetCache;

import java.util.List;

/**
 * Keeps a {@link KeysetCache} in step with a mint's {@code GET /v1/keysets} listing.
 *
 * <p>The listing is the only place a wallet learns which keysets are active and what each one
 * charges, and both change under the operator's hand: a rotation flips {@code active} to false,
 * and a re-priced keyset appears as a new one. Refreshing before a transaction is what keeps the
 * wallet's arithmetic agreeing with the mint's.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 */
@Nut(2)
@Slf4j
public class KeysetDirectory {

    private final String mintUrl;
    private final KeysetCache keysetCache;
    private final KeysetListingFetcher keysetListingFetcher;

    /** Creates a directory over a fresh cache for the given mint. */
    public KeysetDirectory(@NonNull String mintUrl) {
        this(mintUrl, new KeysetCache());
    }

    public KeysetDirectory(@NonNull String mintUrl, @NonNull KeysetCache keysetCache) {
        this(mintUrl, keysetCache, url -> new RequestActiveKeysets(url).execute());
    }

    public KeysetDirectory(
            @NonNull String mintUrl,
            @NonNull KeysetCache keysetCache,
            @NonNull KeysetListingFetcher keysetListingFetcher) {
        this.mintUrl = mintUrl;
        this.keysetCache = keysetCache;
        this.keysetListingFetcher = keysetListingFetcher;
    }

    /**
     * Fetches a mint's NUT-02 keyset listing.
     *
     * <p>Exists so the refresh policy can be exercised without a live mint, and so an
     * application may supply its own transport.
     */
    @FunctionalInterface
    public interface KeysetListingFetcher {

        /** Returns the listing published by the mint at the given base URL. */
        GetActiveKeySetsResponse fetch(String mintUrl);
    }

    /** Returns the cache this directory maintains. */
    public KeysetCache getKeysetCache() {
        return keysetCache;
    }

    /**
     * Fetches the mint's keyset listing and replaces the cached view with it.
     *
     * @return the keysets the mint now publishes
     * @throws IllegalStateException when the mint returns no keyset listing, since a wallet
     *     cannot price or address a transaction without one
     */
    public List<ActiveKeySet> refresh() {
        GetActiveKeySetsResponse response = keysetListingFetcher.fetch(mintUrl);
        List<ActiveKeySet> keysets = requireKeysets(response);

        keysetCache.refresh(keysets);
        log.info("keyset_directory refreshed mint={} keysets={}", mintUrl, keysets.size());
        return keysets;
    }

    private List<ActiveKeySet> requireKeysets(GetActiveKeySetsResponse response) {
        List<ActiveKeySet> keysets = response == null ? null : response.getActiveKeySets();
        if (keysets == null || keysets.isEmpty()) {
            throw new IllegalStateException(
                    "Mint " + mintUrl + " published no keysets. Without the listing the wallet cannot"
                            + " tell which keyset is active nor what it charges."
                            + " Suggestion: verify the mint is reachable and serving GET /v1/keysets.");
        }
        return keysets;
    }
}
