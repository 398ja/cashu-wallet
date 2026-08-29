package xyz.tcheeric.cashu.wallet.proto.nut02;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.ActiveKeySet;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.nut02.KeySetResolver;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The wallet's view of a mint's NUT-02 keyset listing: which keysets are active, and what each
 * one charges per thousand inputs.
 *
 * <p>Both facts are needed before a transaction can be built. Outputs may only name an active
 * keyset, and the fee owed for spending a proof is set by the keyset that issued it, which may
 * well be an inactive one the wallet is draining. Caching the listing keeps that arithmetic
 * available without a round trip per transaction, and {@link #refresh(Collection)} replaces the
 * whole view so a keyset that has since been deactivated stops being chosen for outputs.
 *
 * <p>Instances are safe to share: the cached map is replaced wholesale rather than mutated.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/02.md">NUT-02: Keysets and fees</a>
 */
@Nut(2)
@Slf4j
public class KeysetCache {

    /** Keyset id to listing entry, in the order the mint published them. */
    private volatile Map<String, ActiveKeySet> keysetsById = Map.of();

    /**
     * Replaces the cached listing with the keysets the mint currently publishes.
     *
     * <p>Replacement rather than merge is deliberate: an {@code active} flag that flipped to
     * false, or a keyset the mint has withdrawn, must not survive a refresh.
     *
     * @param keysets the entries of {@code GET /v1/keysets}
     */
    public void refresh(@NonNull Collection<ActiveKeySet> keysets) {
        Map<String, ActiveKeySet> refreshed = new LinkedHashMap<>();
        for (ActiveKeySet keyset : keysets) {
            requireIdentifiedKeyset(keyset);
            refreshed.put(keyset.getId(), keyset);
        }
        this.keysetsById = Collections.unmodifiableMap(refreshed);

        log.info("keyset_cache refreshed keysets={} active={}", refreshed.size(), countActive(refreshed));
    }

    /**
     * Returns the cached entry for a keyset id, or empty when the mint never published it.
     */
    public Optional<ActiveKeySet> findById(@NonNull String keysetId) {
        return Optional.ofNullable(keysetsById.get(keysetId));
    }

    /**
     * Answers whether outputs may be built against this keyset.
     *
     * <p>A keyset the wallet has never heard of is treated as inactive rather than assumed
     * usable, because a spec-conforming mint refuses outputs on anything but an active keyset.
     */
    public boolean isActive(@NonNull String keysetId) {
        return findById(keysetId).map(ActiveKeySet::isActive).orElse(false);
    }

    /**
     * Returns the active keysets denominated in the given unit, in the mint's published order.
     */
    public List<ActiveKeySet> activeKeysets(@NonNull String unit) {
        List<ActiveKeySet> active = new ArrayList<>();
        for (ActiveKeySet keyset : keysetsById.values()) {
            if (keyset.isActive() && unit.equals(keyset.getUnit())) {
                active.add(keyset);
            }
        }
        return List.copyOf(active);
    }

    /**
     * Returns the fee in parts per thousand charged for spending one proof of this keyset.
     *
     * @throws IllegalArgumentException when the keyset is not in the cache, since guessing a fee
     *     of zero would build a transaction the mint refuses as unbalanced
     */
    public int inputFeePpk(@NonNull String keysetId) {
        return findById(keysetId)
                .map(ActiveKeySet::getInputFeePpk)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot price inputs of unknown keyset " + keysetId
                                + ". The mint's keyset listing does not contain it."
                                + " Suggestion: refresh the keyset cache from GET /v1/keysets."));
    }

    /**
     * Exposes the cache as the resolver {@code cashu-lib} prices proofs with, so the wallet and
     * the mint compute the fee from the same code.
     *
     * <p>The resolver hands back a {@link KeySet} carrying only the fee, because fee resolution
     * reads nothing else. Public keys are fetched separately, per keyset, from {@code /v1/keys}.
     */
    public KeySetResolver asKeySetResolver() {
        Map<String, ActiveKeySet> snapshot = keysetsById;
        return keysetId -> Optional.ofNullable(snapshot.get(keysetId)).map(KeysetCache::toPricedKeySet);
    }

    private static KeySet toPricedKeySet(ActiveKeySet keyset) {
        return KeySet.builder()
                .id(keyset.getId())
                .unit(keyset.getUnit())
                .partPerThousand(keyset.getInputFeePpk())
                .build();
    }

    private static void requireIdentifiedKeyset(ActiveKeySet keyset) {
        if (keyset == null || keyset.getId() == null || keyset.getId().isBlank()) {
            throw new IllegalArgumentException(
                    "Keyset listing entry must carry an id. Got: " + keyset);
        }
    }

    private static long countActive(Map<String, ActiveKeySet> keysets) {
        return keysets.values().stream().filter(ActiveKeySet::isActive).count();
    }
}
