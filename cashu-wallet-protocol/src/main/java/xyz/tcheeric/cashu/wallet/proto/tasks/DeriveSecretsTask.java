package xyz.tcheeric.cashu.wallet.proto.tasks;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.bips.bip32.nut.Nut13Derivation;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.nut13.UnsupportedKeysetVersionException;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.KeysetIdVersion;
import xyz.tcheeric.cashu.common.util.Task;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Task for deriving a batch of deterministic secrets and blinding factors using NUT-13 BIP32 derivation.
 *
 * <p>This task derives secrets and corresponding blinding factors from a BIP32 master key using
 * the NUT-13 derivation scheme:
 * <pre>
 * Secret:          m/129372'/0'/{keyset_id_int}'/{counter}'/0
 * Blinding Factor: m/129372'/0'/{keyset_id_int}'/{counter}'/1
 * </pre>
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 */
@Nut(13)
@Slf4j
@ToString(exclude = "masterKey") // Exclude sensitive key material from logs
public class DeriveSecretsTask implements Task<DeriveSecretsTask.DeriveSecretsResult> {

    /** Maximum number of secrets to derive in a single batch. */
    private static final int MAX_COUNT = 1_000;

    /** The only NUT-02 keyset id version for which NUT-13 derivation is defined. */
    private static final KeysetIdVersion DERIVABLE_KEYSET_ID_VERSION = KeysetIdVersion.V1;

    private final DeterministicKey masterKey;
    private final KeysetId keysetId;
    private final int startCounter;
    private final int count;

    public DeriveSecretsTask(DeterministicKey masterKey, KeysetId keysetId, int startCounter, int count) {
        if (masterKey == null) {
            throw new IllegalArgumentException("Master key must not be null");
        }
        if (keysetId == null) {
            throw new IllegalArgumentException("Keyset ID must not be null");
        }
        if (count <= 0 || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                String.format("Count must be between 1 and %d, got: %d", MAX_COUNT, count)
            );
        }
        if (startCounter < 0) {
            throw new IllegalArgumentException("Start counter must be non-negative, got: " + startCounter);
        }
        // Fail fast if the counter range would overflow during execution
        try {
            Math.addExact(startCounter, count - 1);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                String.format("Counter range overflows: startCounter=%d, count=%d", startCounter, count), e);
        }
        this.masterKey = masterKey;
        this.keysetId = requireDerivableKeysetVersion(keysetId);
        this.startCounter = startCounter;
        this.count = count;
    }

    /**
     * Rejects a keyset whose id version this wallet cannot derive NUT-13 secrets for.
     *
     * <p>Only version 1 ids are derivable. Applying version 1 derivation to a version 2 keyset
     * produces secrets the mint never signed, so restore recovers nothing and the result is
     * indistinguishable from an empty wallet. Failing loudly is the point.
     */
    private static KeysetId requireDerivableKeysetVersion(KeysetId keysetId) {
        KeysetIdVersion version = keysetId.getVersion();
        if (version != DERIVABLE_KEYSET_ID_VERSION) {
            log.warn("derive_secrets keyset_not_restorable keyset={} version={} impact=rejected",
                keysetId, version);
            throw new UnsupportedKeysetVersionException(keysetId, version);
        }
        return keysetId;
    }

    @Override
    public DeriveSecretsResult execute() {
        log.info("derive_secrets task_started keyset={} start_counter={} count={}",
            keysetId, startCounter, count);

        List<DeterministicSecret> secrets = new ArrayList<>(count);
        List<byte[]> blindingFactors = new ArrayList<>(count);

        String keysetIdHex = keysetId.toString();

        for (int i = 0; i < count; i++) {
            // SW-12: overflow guard
            final int counter = Math.addExact(startCounter, i);

            try {
                Nut13Derivation.SecretAndBlindingFactor pair =
                    Nut13Derivation.deriveSecretAndBlindingFactor(
                        masterKey,
                        keysetIdHex,
                        counter
                    );

                DeterministicSecret secret = DeterministicSecret.create(
                    pair.secret(),
                    keysetId,
                    counter
                );

                secrets.add(secret);
                blindingFactors.add(pair.blindingFactor());

            } catch (UnsupportedKeysetVersionException e) {
                throw e;
            } catch (Exception e) {
                log.error("derive_secrets task_failed keyset={} counter={} impact=abort_batch",
                    keysetId, counter, e);
                // SW-09: sanitized exception message
                throw new IllegalStateException(
                    String.format("Failed to derive secret at counter %d", counter),
                    e
                );
            }
        }

        log.info("derive_secrets task_completed keyset={} derived_count={}",
            keysetId, secrets.size());

        return new DeriveSecretsResult(secrets, blindingFactors);
    }

    /**
     * Result containing derived secrets and their corresponding blinding factors.
     *
     * <p>Call {@link #clearSensitiveData()} after blinding factors have been consumed
     * to zero sensitive key material from memory.
     */
    @ToString
    public static class DeriveSecretsResult {

        private final List<DeterministicSecret> secrets;
        private final List<byte[]> blindingFactors;
        private volatile boolean cleared;

        public DeriveSecretsResult(List<DeterministicSecret> secrets, List<byte[]> blindingFactors) {
            this.secrets = secrets;
            this.blindingFactors = blindingFactors;
        }

        /** Returns an unmodifiable view of the secrets list. */
        public List<DeterministicSecret> getSecrets() {
            return Collections.unmodifiableList(secrets);
        }

        /** Returns an unmodifiable view of the blinding factors list. */
        public List<byte[]> getBlindingFactors() {
            if (cleared) {
                throw new IllegalStateException("Blinding factors have been cleared and can no longer be accessed");
            }
            return Collections.unmodifiableList(blindingFactors);
        }

        public int getCount() {
            return secrets.size();
        }

        /** Returns true if sensitive data has been cleared. */
        public boolean isCleared() {
            return cleared;
        }

        public void validate() {
            if (secrets.size() != blindingFactors.size()) {
                throw new IllegalStateException(String.format(
                    "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                    secrets.size(), blindingFactors.size()
                ));
            }
        }

        /**
         * Zeros all blinding factor byte arrays to minimize sensitive data lifetime in memory.
         * After calling this method, {@link #getBlindingFactors()} will throw {@link IllegalStateException}.
         */
        public void clearSensitiveData() {
            for (byte[] bf : blindingFactors) {
                if (bf != null) {
                    Arrays.fill(bf, (byte) 0);
                }
            }
            cleared = true;
        }
    }
}
