package xyz.tcheeric.cashu.wallet.proto.tasks;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DeriveSecretsTask}.
 *
 * <p>These tests verify the task's ability to derive deterministic secrets and blinding
 * factors using real cryptographic operations from bip-utils.
 */
class DeriveSecretsTaskTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_PASSPHRASE = "";
    private static final String TEST_KEYSET_ID = "009a1f293253e41e";

    private DeterministicKey masterKey;
    private KeysetId keysetId;

    @BeforeEach
    void setUp() {
        // Derive master key from test mnemonic
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);
        keysetId = KeysetId.fromString(TEST_KEYSET_ID);
    }

    /**
     * Ensures execute derives the exact number of requested secrets and blinding factors.
     */
    @Test
    void shouldDeriveRequestedNumberOfSecrets() {
        // Arrange
        int count = 10;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, count);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSecrets()).hasSize(count);
        assertThat(result.getBlindingFactors()).hasSize(count);
        assertThat(result.getCount()).isEqualTo(count);
    }

    /**
     * Ensures derived secrets include the expected metadata.
     */
    @Test
    void shouldPopulateSecretMetadataForEachEntry() {
        // Arrange
        int startCounter = 5;
        int count = 3;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, startCounter, count);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        List<DeterministicSecret> secrets = result.getSecrets();
        for (int i = 0; i < count; i++) {
            DeterministicSecret secret = secrets.get(i);
            assertThat(secret.hasMetadata()).isTrue();
            assertThat(secret.getKeysetId()).isEqualTo(keysetId);
            assertThat(secret.getCounter()).isEqualTo(startCounter + i);
            assertThat(secret.getDerivationPath()).isNotNull();
        }
    }

    /**
     * Ensures repeated executions with identical inputs produce identical outputs.
     */
    @Test
    void shouldBeDeterministicForSameInputs() {
        // Arrange
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Assert
        assertThat(result1.getSecrets()).hasSameSizeAs(result2.getSecrets());
        for (int i = 0; i < result1.getSecrets().size(); i++) {
            assertThat(result1.getSecrets().get(i).getData())
                .containsExactly(result2.getSecrets().get(i).getData());
            assertThat(result1.getBlindingFactors().get(i))
                .containsExactly(result2.getBlindingFactors().get(i));
        }
    }

    /**
     * Ensures different counters produce distinct secrets.
     */
    @Test
    void shouldProduceDifferentSecretsWhenCountersDiffer() {
        // Arrange
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 1, 1);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Assert
        assertThat(Arrays.equals(
            result1.getSecrets().get(0).getData(),
            result2.getSecrets().get(0).getData()
        )).isFalse();
    }

    /**
     * Ensures the recommended batch size of 100 is supported.
     */
    @Test
    void shouldDeriveHundredSecretsWhenRequested() {
        // Arrange
        int batchSize = 100;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, batchSize);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        assertThat(result.getSecrets()).hasSize(batchSize);
        assertThat(result.getBlindingFactors()).hasSize(batchSize);
    }

    /**
     * Ensures blinding factors are exactly 32 bytes.
     */
    @Test
    void shouldProduce32ByteBlindingFactors() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        for (int i = 0; i < result.getBlindingFactors().size(); i++) {
            byte[] blindingFactor = result.getBlindingFactors().get(i);
            assertThat(blindingFactor)
                .describedAs("Blinding factor at index %s should be 32 bytes", i)
                .isNotNull()
                .hasSize(32);
        }
    }

    /**
     * Ensures derived secrets are exactly 32 bytes.
     */
    @Test
    void shouldProduce32ByteSecrets() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        for (int i = 0; i < result.getSecrets().size(); i++) {
            byte[] secret = result.getSecrets().get(i).getData();
            assertThat(secret)
                .describedAs("Secret at index %s should be 32 bytes", i)
                .isNotNull()
                .hasSize(32);
        }
    }

    /**
     * Ensures the validate method accepts matching list sizes.
     */
    @Test
    void shouldPassValidationWhenListSizesMatch() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        assertThatCode(result::validate).doesNotThrowAnyException();
    }

    /**
     * Ensures large start counters are supported.
     */
    @Test
    void shouldSupportLargeStartCounter() {
        // Arrange
        int startCounter = 1000;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, startCounter, 10);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        assertThat(result.getSecrets()).hasSize(10);
        assertThat(result.getSecrets().get(0).getCounter()).isEqualTo(startCounter);
        assertThat(result.getSecrets().get(9).getCounter()).isEqualTo(startCounter + 9);
    }

    /**
     * Ensures all derived secrets retain the input keyset ID.
     */
    @Test
    void shouldAssignKeysetIdToAllSecrets() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Assert
        for (DeterministicSecret secret : result.getSecrets()) {
            assertThat(secret.getKeysetId()).isEqualTo(keysetId);
        }
    }

    /**
     * Ensures changing the mnemonic yields different secrets.
     */
    @Test
    void shouldProduceDifferentSecretsWhenMnemonicDiffers() {
        // Arrange
        String mnemonic2 = "legal winner thank year wave sausage worth useful legal winner thank yellow";
        DeterministicKey masterKey2 = Bip39.mnemonicToMasterKey(mnemonic2, TEST_PASSPHRASE);

        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey2, keysetId, 0, 1);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Assert
        assertThat(Arrays.equals(
            result1.getSecrets().get(0).getData(),
            result2.getSecrets().get(0).getData()
        )).isFalse();
    }

    /**
     * Ensures changing the keyset ID yields different secrets.
     */
    @Test
    void shouldProduceDifferentSecretsWhenKeysetDiffers() {
        // Arrange
        KeysetId keysetId2 = KeysetId.fromString("009a1f293253e41f");

        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId2, 0, 1);

        // Act
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Assert
        assertThat(Arrays.equals(
            result1.getSecrets().get(0).getData(),
            result2.getSecrets().get(0).getData()
        )).isFalse();
    }

    // ========================================
    // SW-02: Bounds validation tests
    // ========================================

    /**
     * Ensures the task rejects count exceeding the maximum.
     */
    @Test
    void shouldRejectCountExceedingMaximum() {
        assertThatThrownBy(() -> new DeriveSecretsTask(masterKey, keysetId, 0, 1_001))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1000");
    }

    /**
     * Ensures the task rejects zero count.
     */
    @Test
    void shouldRejectZeroCount() {
        assertThatThrownBy(() -> new DeriveSecretsTask(masterKey, keysetId, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Ensures the task rejects negative count.
     */
    @Test
    void shouldRejectNegativeCount() {
        assertThatThrownBy(() -> new DeriveSecretsTask(masterKey, keysetId, 0, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Ensures the task rejects negative start counter.
     */
    @Test
    void shouldRejectNegativeStartCounter() {
        assertThatThrownBy(() -> new DeriveSecretsTask(masterKey, keysetId, -1, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    /**
     * Ensures the task accepts maximum allowed count.
     */
    @Test
    void shouldAcceptMaxAllowedCount() {
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 1_000);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();
        assertThat(result.getSecrets()).hasSize(1_000);
    }

    // ========================================
    // SW-04: Sensitive data cleanup tests
    // ========================================

    /**
     * Ensures clearSensitiveData zeros all blinding factor byte arrays.
     */
    @Test
    void shouldZeroBlindingFactorsOnClear() {
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Capture references to blinding factor arrays before clearing
        List<byte[]> bfRefs = new java.util.ArrayList<>(result.getBlindingFactors());

        // Verify blinding factors are non-zero before clear
        for (byte[] bf : bfRefs) {
            assertThat(bf).isNotEqualTo(new byte[32]);
        }

        result.clearSensitiveData();

        // Verify all blinding factors are now zero (via captured references)
        for (byte[] bf : bfRefs) {
            assertThat(bf).isEqualTo(new byte[32]);
        }

        // Verify isCleared flag is set
        assertThat(result.isCleared()).isTrue();
    }

    /**
     * Ensures getBlindingFactors throws after clearSensitiveData has been called.
     */
    @Test
    void shouldThrowWhenAccessingBlindingFactorsAfterClear() {
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        result.clearSensitiveData();

        assertThatThrownBy(result::getBlindingFactors)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cleared");
    }

    /**
     * Ensures the constructor rejects null master key.
     */
    @Test
    void shouldRejectNullMasterKey() {
        assertThatThrownBy(() -> new DeriveSecretsTask(null, keysetId, 0, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Master key");
    }

    /**
     * Ensures the constructor rejects null keyset ID.
     */
    @Test
    void shouldRejectNullKeysetId() {
        assertThatThrownBy(() -> new DeriveSecretsTask(masterKey, null, 0, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Keyset ID");
    }

    // ========================================
    // SW-10: Unmodifiable list tests
    // ========================================

    /**
     * Ensures getSecrets returns an unmodifiable list.
     */
    @Test
    void shouldReturnUnmodifiableSecretsList() {
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        assertThatThrownBy(() -> result.getSecrets().add(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Ensures getBlindingFactors returns an unmodifiable list.
     */
    @Test
    void shouldReturnUnmodifiableBlindingFactorsList() {
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        assertThatThrownBy(() -> result.getBlindingFactors().add(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
