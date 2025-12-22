package xyz.tcheeric.cashu.wallet.proto;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.bips.bip32.nut.Nut13Derivation;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.tasks.DeriveSecretsTask;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for NUT-13 wallet recovery components.
 *
 * <p>Tests the complete workflow from mnemonic to proof recovery:
 * <ul>
 *   <li>Deterministic secret derivation from BIP39 mnemonic</li>
 *   <li>Batch secret generation with blinding factors</li>
 *   <li>Restore request building with blinded messages</li>
 *   <li>Multi-keyset recovery scenarios</li>
 * </ul>
 *
 * @see DeriveSecretsTask
 * @see RestoreRequestBuilder
 */
class WalletRecoveryIntegrationTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_PASSPHRASE = "";
    private static final String TEST_KEYSET_ID = "009a1f293253e41e";

    private DeterministicKey masterKey;
    private KeysetId keysetId;
    private RestoreRequestBuilder requestBuilder;

    @BeforeEach
    void setUp() {
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);
        keysetId = KeysetId.fromString(TEST_KEYSET_ID);
        requestBuilder = new RestoreRequestBuilder();
    }

    /**
     * Test 4.2.1: Verify DeriveSecretsTask produces deterministic, reproducible secrets.
     */
    @Test
    void shouldProduceReproducibleSecretsFromSameMnemonic() {
        // Arrange
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 10);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 0, 10);

        // Act
        var result1 = task1.execute();
        var result2 = task2.execute();

        // Assert
        assertThat(result1.getSecrets()).hasSize(10);
        assertThat(result2.getSecrets()).hasSize(10);
        assertThat(result1.getBlindingFactors()).hasSize(10);
        assertThat(result2.getBlindingFactors()).hasSize(10);

        // Verify secrets are identical
        for (int i = 0; i < 10; i++) {
            assertThat(result1.getSecrets().get(i).getData())
                .as("Secret at index %d should be identical", i)
                .isEqualTo(result2.getSecrets().get(i).getData());

            assertThat(result1.getBlindingFactors().get(i))
                .as("Blinding factor at index %d should be identical", i)
                .isEqualTo(result2.getBlindingFactors().get(i));
        }
    }

    /**
     * Test 4.2.2: Verify derived secrets contain proper metadata.
     */
    @Test
    void shouldIncludeMetadataInDerivedSecrets() {
        // Arrange
        int startCounter = 5;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, startCounter, 3);

        // Act
        var result = task.execute();

        // Assert
        List<DeterministicSecret> secrets = result.getSecrets();
        for (int i = 0; i < 3; i++) {
            DeterministicSecret secret = secrets.get(i);

            assertThat(secret.hasMetadata()).isTrue();
            assertThat(secret.getKeysetId()).isEqualTo(keysetId);
            assertThat(secret.getCounter()).isEqualTo(startCounter + i);
            assertThat(secret.getDerivationPath()).isNotNull();
            assertThat(secret.getData()).hasSize(32);
        }
    }

    /**
     * Test 4.2.3: Verify different counters produce different secrets.
     */
    @Test
    void shouldProduceDifferentSecretsForDifferentCounters() {
        // Arrange
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 1, 1);

        // Act
        var result1 = task1.execute();
        var result2 = task2.execute();

        // Assert
        assertThat(result1.getSecrets().get(0).getData())
            .as("Different counters should produce different secrets")
            .isNotEqualTo(result2.getSecrets().get(0).getData());

        assertThat(result1.getBlindingFactors().get(0))
            .as("Different counters should produce different blinding factors")
            .isNotEqualTo(result2.getBlindingFactors().get(0));
    }

    /**
     * Test 4.2.4: Verify different keysets produce different secrets.
     */
    @Test
    void shouldProduceDifferentSecretsForDifferentKeysets() {
        // Arrange
        KeysetId keysetId2 = KeysetId.fromString("009a1f293253e41f");
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId2, 0, 1);

        // Act
        var result1 = task1.execute();
        var result2 = task2.execute();

        // Assert
        assertThat(result1.getSecrets().get(0).getData())
            .as("Different keysets should produce different secrets")
            .isNotEqualTo(result2.getSecrets().get(0).getData());
    }

    /**
     * Test 4.2.5: Verify batch derivation produces correct count.
     */
    @Test
    void shouldDeriveBatchOfSecretsWithCorrectCount() {
        // Arrange - NUT-13 recommended batch size
        int batchSize = 100;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, batchSize);

        // Act
        var result = task.execute();

        // Assert
        assertThat(result.getSecrets()).hasSize(batchSize);
        assertThat(result.getBlindingFactors()).hasSize(batchSize);
        assertThat(result.getCount()).isEqualTo(batchSize);

        // Verify all secrets are 32 bytes
        result.getSecrets().forEach(secret ->
            assertThat(secret.getData()).hasSize(32)
        );

        // Verify all blinding factors are 32 bytes
        result.getBlindingFactors().forEach(factor ->
            assertThat(factor).hasSize(32)
        );
    }

    /**
     * Test 4.2.6: Verify RestoreRequestBuilder creates valid blinded messages.
     */
    @Test
    void shouldCreateValidBlindedMessagesFromSecrets() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        var deriveResult = task.execute();
        int amount = 1;

        // Act
        List<BlindedMessage> blindedMessages = requestBuilder.createBlindedMessages(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            amount
        );

        // Assert
        assertThat(blindedMessages).hasSize(5);

        blindedMessages.forEach(msg -> {
            assertThat(msg.getAmount()).isEqualTo(amount);
            assertThat(msg.getKeySetId()).isEqualTo(keysetId);
            assertThat(msg.getBlindedMessage()).isNotNull();
            byte[] uncompressed = msg.getBlindedMessage().getUncompressedBytes();
            assertThat(uncompressed).isNotNull();
            // Uncompressed public key should be 64 bytes (X and Y coordinates)
            assertThat(uncompressed).hasSize(64);
        });
    }

    /**
     * Test 4.2.7: Verify blinded messages are deterministic (same inputs = same outputs).
     */
    @Test
    void shouldCreateDeterministicBlindedMessages() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        var deriveResult = task.execute();
        int amount = 1;

        // Act
        List<BlindedMessage> messages1 = requestBuilder.createBlindedMessages(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            amount
        );
        List<BlindedMessage> messages2 = requestBuilder.createBlindedMessages(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            amount
        );

        // Assert
        assertThat(messages1).hasSize(3);
        assertThat(messages2).hasSize(3);

        for (int i = 0; i < 3; i++) {
            assertThat(messages1.get(i).getBlindedMessage().getBytes())
                .as("Blinded message at index %d should be identical", i)
                .isEqualTo(messages2.get(i).getBlindedMessage().getBytes());
        }
    }

    /**
     * Test 4.2.8: Verify RestoreRequestBuilder builds valid PostRestoreRequest.
     */
    @Test
    void shouldBuildValidRestoreRequest() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 10);
        var deriveResult = task.execute();

        List<BlindedMessage> blindedMessages = requestBuilder.createBlindedMessages(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            1
        );

        // Act
        PostRestoreRequest request = requestBuilder.buildRequest(blindedMessages);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getBlindedMessages()).hasSize(10);
        assertThat(request.getBlindedMessages()).isEqualTo(blindedMessages);
    }

    /**
     * Test 4.2.9: Verify RestoreRequestBuilder builds request from secrets directly.
     */
    @Test
    void shouldBuildRequestDirectlyFromSecrets() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        var deriveResult = task.execute();
        int amount = 8;

        // Act
        PostRestoreRequest request = requestBuilder.buildRequestFromSecrets(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            amount
        );

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getBlindedMessages()).hasSize(5);

        // Verify all messages have correct amount
        request.getBlindedMessages().forEach(msg ->
            assertThat(msg.getAmount()).isEqualTo(amount)
        );
    }

    /**
     * Test 4.2.10: Verify batch request building with NUT-13 recommended size.
     */
    @Test
    void shouldBuildBatchRequestWithRecommendedSize() {
        // Arrange - NUT-13 recommends 100 token batches
        int batchSize = 100;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, batchSize);
        var deriveResult = task.execute();

        // Act
        PostRestoreRequest request = requestBuilder.buildRequestFromSecrets(
            deriveResult.getSecrets(),
            deriveResult.getBlindingFactors(),
            1
        );

        // Assert
        assertThat(request.getBlindedMessages()).hasSize(batchSize);
    }

    /**
     * Test 4.2.11: Verify RestoreRequestBuilder handles different amounts correctly.
     */
    @Test
    void shouldHandleDifferentAmountsInBlindedMessages() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        var deriveResult = task.execute();
        int[] amounts = {1, 2, 4, 8, 16};

        // Act & Assert
        for (int amount : amounts) {
            PostRestoreRequest request = requestBuilder.buildRequestFromSecrets(
                deriveResult.getSecrets(),
                deriveResult.getBlindingFactors(),
                amount
            );

            request.getBlindedMessages().forEach(msg ->
                assertThat(msg.getAmount())
                    .as("Message should have amount %d", amount)
                    .isEqualTo(amount)
            );
        }
    }

    /**
     * Test 4.2.12: Verify derivation with large counter values works correctly.
     */
    @Test
    void shouldHandleLargeCounterValues() {
        // Arrange
        int largeCounter = 10000;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, largeCounter, 10);

        // Act
        var result = task.execute();

        // Assert
        assertThat(result.getSecrets()).hasSize(10);
        assertThat(result.getSecrets().get(0).getCounter()).isEqualTo(largeCounter);
        assertThat(result.getSecrets().get(9).getCounter()).isEqualTo(largeCounter + 9);

        // Verify all secrets are properly formed
        result.getSecrets().forEach(secret -> {
            assertThat(secret.getData()).hasSize(32);
            assertThat(secret.hasMetadata()).isTrue();
        });
    }

    /**
     * Test 4.2.13: Verify RestoreRequestBuilder validates input sizes.
     */
    @Test
    void shouldValidateInputSizesInRequestBuilder() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        var deriveResult = task.execute();

        List<DeterministicSecret> secrets = deriveResult.getSecrets();
        List<byte[]> tooFewFactors = deriveResult.getBlindingFactors().subList(0, 3);

        // Act & Assert
        assertThatThrownBy(() ->
            requestBuilder.createBlindedMessages(secrets, tooFewFactors, 1)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mismatch");
    }

    /**
     * Test 4.2.14: Verify RestoreRequestBuilder validates keyset consistency.
     */
    @Test
    void shouldValidateKeysetConsistency() {
        // Arrange - mix secrets from different keysets
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        KeysetId keysetId2 = KeysetId.fromString("009a1f293253e41f");
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId2, 0, 1);

        var result1 = task1.execute();
        var result2 = task2.execute();

        List<DeterministicSecret> mixedSecrets = new java.util.ArrayList<>(result1.getSecrets());
        mixedSecrets.add(result2.getSecrets().get(0));

        List<byte[]> mixedFactors = new java.util.ArrayList<>(result1.getBlindingFactors());
        mixedFactors.add(result2.getBlindingFactors().get(0));

        // Act & Assert
        assertThatThrownBy(() ->
            requestBuilder.createBlindedMessages(mixedSecrets, mixedFactors, 1)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same keyset ID");
    }

    /**
     * Test 4.2.15: Verify RestoreRequestBuilder validates amount is positive.
     */
    @Test
    void shouldValidateAmountIsPositive() {
        // Arrange
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 3);
        var deriveResult = task.execute();

        // Act & Assert
        assertThatThrownBy(() ->
            requestBuilder.createBlindedMessages(
                deriveResult.getSecrets(),
                deriveResult.getBlindingFactors(),
                0
            )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
            requestBuilder.createBlindedMessages(
                deriveResult.getSecrets(),
                deriveResult.getBlindingFactors(),
                -1
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test 4.2.16: Verify multi-keyset derivation produces independent secrets.
     */
    @Test
    void shouldProduceIndependentSecretsForMultipleKeysets() {
        // Arrange
        KeysetId keyset1 = KeysetId.fromString("009a1f293253e41e");
        KeysetId keyset2 = KeysetId.fromString("009a1f293253e41f");
        KeysetId keyset3 = KeysetId.fromString("009a1f293253e420");

        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keyset1, 0, 5);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keyset2, 0, 5);
        DeriveSecretsTask task3 = new DeriveSecretsTask(masterKey, keyset3, 0, 5);

        // Act
        var result1 = task1.execute();
        var result2 = task2.execute();
        var result3 = task3.execute();

        // Assert - verify all secrets are different between keysets
        for (int i = 0; i < 5; i++) {
            byte[] secret1 = result1.getSecrets().get(i).getData();
            byte[] secret2 = result2.getSecrets().get(i).getData();
            byte[] secret3 = result3.getSecrets().get(i).getData();

            assertThat(secret1)
                .as("Keyset 1 and 2 should produce different secrets at index %d", i)
                .isNotEqualTo(secret2);

            assertThat(secret1)
                .as("Keyset 1 and 3 should produce different secrets at index %d", i)
                .isNotEqualTo(secret3);

            assertThat(secret2)
                .as("Keyset 2 and 3 should produce different secrets at index %d", i)
                .isNotEqualTo(secret3);
        }
    }

    /**
     * Test 4.2.17: Verify direct derivation using Nut13Derivation matches task output.
     */
    @Test
    void shouldMatchDirectNut13DerivationResults() {
        // Arrange
        int counter = 0;

        // Direct derivation
        var directPair = Nut13Derivation.deriveSecretAndBlindingFactor(
            masterKey,
            keysetId.toString(),
            counter
        );

        // Task derivation
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, counter, 1);
        var taskResult = task.execute();

        // Assert
        assertThat(taskResult.getSecrets().get(0).getData())
            .as("Task-derived secret should match direct derivation")
            .isEqualTo(directPair.secret());

        assertThat(taskResult.getBlindingFactors().get(0))
            .as("Task-derived blinding factor should match direct derivation")
            .isEqualTo(directPair.blindingFactor());
    }

    /**
     * Test 4.2.18: Verify sequential batch processing maintains counter continuity.
     */
    @Test
    void shouldMaintainCounterContinuityAcrossBatches() {
        // Arrange
        int batchSize = 100;

        // Derive three sequential batches
        DeriveSecretsTask batch1 = new DeriveSecretsTask(masterKey, keysetId, 0, batchSize);
        DeriveSecretsTask batch2 = new DeriveSecretsTask(masterKey, keysetId, batchSize, batchSize);
        DeriveSecretsTask batch3 = new DeriveSecretsTask(masterKey, keysetId, batchSize * 2, batchSize);

        // Act
        var result1 = batch1.execute();
        var result2 = batch2.execute();
        var result3 = batch3.execute();

        // Assert - verify counters are sequential
        assertThat(result1.getSecrets().get(0).getCounter()).isEqualTo(0);
        assertThat(result1.getSecrets().get(99).getCounter()).isEqualTo(99);

        assertThat(result2.getSecrets().get(0).getCounter()).isEqualTo(100);
        assertThat(result2.getSecrets().get(99).getCounter()).isEqualTo(199);

        assertThat(result3.getSecrets().get(0).getCounter()).isEqualTo(200);
        assertThat(result3.getSecrets().get(99).getCounter()).isEqualTo(299);

        // Verify no overlapping secrets
        assertThat(result1.getSecrets().get(99).getData())
            .isNotEqualTo(result2.getSecrets().get(0).getData());
        assertThat(result2.getSecrets().get(99).getData())
            .isNotEqualTo(result3.getSecrets().get(0).getData());
    }
}
