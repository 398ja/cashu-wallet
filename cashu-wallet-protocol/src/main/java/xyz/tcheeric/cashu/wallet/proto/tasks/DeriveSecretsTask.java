package xyz.tcheeric.cashu.wallet.proto.tasks;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.java.Log;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.bips.bip32.nut.Nut13Derivation;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.util.Task;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.ArrayList;
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
 * <p>The same mnemonic + keyset + counter combination will always produce the same secret
 * and blinding factor, enabling deterministic wallet recovery.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * DeterministicKey masterKey = Bip39.mnemonicToMasterKey(mnemonic, passphrase);
 * KeysetId keysetId = new KeysetId("009a1f293253e41e");
 *
 * // Derive 100 secrets starting from counter 0
 * DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 100);
 * DeriveSecretsResult result = task.execute();
 *
 * List<DeterministicSecret> secrets = result.getSecrets();
 * List<byte[]> blindingFactors = result.getBlindingFactors();
 * }</pre>
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 * @see Nut13Derivation
 * @see DeterministicSecret
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
@Nut(13)
@Log
@AllArgsConstructor
@ToString(exclude = "masterKey") // Exclude sensitive key material from logs
public class DeriveSecretsTask implements Task<DeriveSecretsTask.DeriveSecretsResult> {

    /**
     * BIP32 master key derived from mnemonic phrase.
     * This key is used as the root for all NUT-13 derivations.
     */
    private final DeterministicKey masterKey;

    /**
     * Keyset ID for which to derive secrets.
     * This determines the keyset_id_int component in the derivation path.
     */
    private final KeysetId keysetId;

    /**
     * Starting counter value for the first secret in this batch.
     * The counter increments by 1 for each subsequent secret.
     */
    private final int startCounter;

    /**
     * Number of secrets to derive in this batch.
     * Recommended batch size is 100 per NUT-13 specification.
     */
    private final int count;

    /**
     * Derives a batch of deterministic secrets and their corresponding blinding factors.
     *
     * <p>For each counter value from {@code startCounter} to {@code startCounter + count - 1},
     * this method:
     * <ol>
     *   <li>Derives the secret using path: m/129372'/0'/{keyset_id_int}'/{counter}'/0</li>
     *   <li>Derives the blinding factor using path: m/129372'/0'/{keyset_id_int}'/{counter}'/1</li>
     *   <li>Creates a DeterministicSecret with metadata (keyset ID, counter, derivation path)</li>
     * </ol>
     *
     * <p>The derivation is deterministic - the same inputs always produce the same outputs.
     *
     * @return DeriveSecretsResult containing lists of secrets and blinding factors
     * @throws IllegalStateException if derivation fails due to invalid key or parameters
     */
    @Override
    public DeriveSecretsResult execute() {
        log.info(String.format("derive_secrets_started keyset=%s start_counter=%d count=%d",
            keysetId, startCounter, count));

        List<DeterministicSecret> secrets = new ArrayList<>(count);
        List<byte[]> blindingFactors = new ArrayList<>(count);

        String keysetIdHex = keysetId.toString();

        for (int i = 0; i < count; i++) {
            final int counter = startCounter + i;

            try {
                // Derive both secret and blinding factor using Nut13Derivation
                Nut13Derivation.SecretAndBlindingFactor pair =
                    Nut13Derivation.deriveSecretAndBlindingFactor(
                        masterKey,
                        keysetIdHex,
                        counter
                    );

                // Create DeterministicSecret with metadata for tracking
                DeterministicSecret secret = DeterministicSecret.create(
                    pair.secret(),
                    keysetId,
                    counter
                );

                secrets.add(secret);
                blindingFactors.add(pair.blindingFactor());

            } catch (Exception e) {
                log.severe(String.format(
                    "derive_secrets_failed keyset=%s counter=%d error=%s",
                    keysetId, counter, e.getMessage()
                ));
                throw new IllegalStateException(
                    String.format("Failed to derive secret at counter %d: %s", counter, e.getMessage()),
                    e
                );
            }
        }

        log.info(String.format("derive_secrets_completed keyset=%s derived_count=%d",
            keysetId, secrets.size()));

        return new DeriveSecretsResult(secrets, blindingFactors);
    }

    /**
     * Result record containing derived secrets and their corresponding blinding factors.
     *
     * <p>This record maintains a one-to-one correspondence between secrets and blinding factors:
     * {@code secrets.get(i)} corresponds to {@code blindingFactors.get(i)} for all valid indices.
     *
     * <p>Both lists are guaranteed to have the same size, matching the {@code count} parameter
     * from the task.
     *
     * @param secrets List of deterministic secrets with metadata (keyset ID, counter, path)
     * @param blindingFactors List of blinding factors (32 bytes each) in the same order as secrets
     */
    @Getter
    @AllArgsConstructor
    @ToString
    public static class DeriveSecretsResult {

        /**
         * Derived deterministic secrets with full metadata.
         * Each secret includes its derivation path, counter, and keyset ID.
         */
        private final List<DeterministicSecret> secrets;

        /**
         * Derived blinding factors (r values) corresponding to each secret.
         * Each blinding factor is 32 bytes and matches the secret at the same index.
         */
        private final List<byte[]> blindingFactors;

        /**
         * Returns the number of secrets/blinding factor pairs in this result.
         *
         * @return count of derived secrets (always equals blindingFactors.size())
         */
        public int getCount() {
            return secrets.size();
        }

        /**
         * Validates that secrets and blinding factors lists have matching sizes.
         *
         * @throws IllegalStateException if list sizes don't match
         */
        public void validate() {
            if (secrets.size() != blindingFactors.size()) {
                throw new IllegalStateException(String.format(
                    "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                    secrets.size(), blindingFactors.size()
                ));
            }
        }
    }
}
