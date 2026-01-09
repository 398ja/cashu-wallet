package xyz.tcheeric.cashu.wallet.proto.builders;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.wallet.proto.tasks.DeriveSecretsTask;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RestoreRequestBuilder}.
 */
class RestoreRequestBuilderTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_KEYSET_ID = "009a1f293253e41e";

    private RestoreRequestBuilder builder;
    private List<DeterministicSecret> testSecrets;
    private List<byte[]> testBlindingFactors;
    private KeysetId keysetId;

    @BeforeEach
    void setUp() {
        builder = new RestoreRequestBuilder();
        keysetId = KeysetId.fromString(TEST_KEYSET_ID);

        // Derive real secrets and blinding factors for testing
        DeterministicKey masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, "");
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        testSecrets = result.getSecrets();
        testBlindingFactors = result.getBlindingFactors();
    }

    @Test
    void testCreateBlindedMessagesSucceeds() {
        // Given
        int amount = 1;

        // When
        List<BlindedMessage> blindedMessages = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            amount
        );

        // Then
        assertNotNull(blindedMessages);
        assertEquals(testSecrets.size(), blindedMessages.size());
    }

    @Test
    void testBlindedMessagesHaveCorrectProperties() {
        // Given
        int amount = 8;

        // When
        List<BlindedMessage> blindedMessages = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            amount
        );

        // Then
        for (BlindedMessage msg : blindedMessages) {
            assertEquals(amount, msg.getAmount());
            assertEquals(keysetId, msg.getKeySetId());
            assertNotNull(msg.getBlindedMessage());
            assertNotNull(msg.getBlindedMessage().getBytes());
        }
    }

    @Test
    void testCreateBlindedMessagesIsDeterministic() {
        // Given
        int amount = 1;

        // When
        List<BlindedMessage> messages1 = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            amount
        );
        List<BlindedMessage> messages2 = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            amount
        );

        // Then - same inputs produce same blinded messages
        assertEquals(messages1.size(), messages2.size());
        for (int i = 0; i < messages1.size(); i++) {
            assertArrayEquals(
                messages1.get(i).getBlindedMessage().getBytes(),
                messages2.get(i).getBlindedMessage().getBytes(),
                "Blinded message at index " + i + " should be identical"
            );
        }
    }

    @Test
    void testCreateBlindedMessagesThrowsOnEmptySecrets() {
        // Given
        List<DeterministicSecret> emptySecrets = List.of();
        List<byte[]> emptyFactors = List.of();

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            builder.createBlindedMessages(emptySecrets, emptyFactors, 1)
        );
    }

    @Test
    void testCreateBlindedMessagesThrowsOnSizeMismatch() {
        // Given
        List<byte[]> tooFewFactors = testBlindingFactors.subList(0, 3);

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> builder.createBlindedMessages(testSecrets, tooFewFactors, 1)
        );
        assertTrue(exception.getMessage().contains("mismatch"));
    }

    @Test
    void testCreateBlindedMessagesThrowsOnInvalidAmount() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            builder.createBlindedMessages(testSecrets, testBlindingFactors, 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
            builder.createBlindedMessages(testSecrets, testBlindingFactors, -1)
        );
    }

    @Test
    void testCreateBlindedMessagesThrowsOnMixedKeysetIds() {
        // Given - create secrets with different keyset IDs
        List<DeterministicSecret> mixedSecrets = new ArrayList<>(testSecrets);
        KeysetId differentKeysetId = KeysetId.fromString("009a1f293253e41f");
        DeterministicKey masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, "");
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, differentKeysetId, 0, 1);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();
        mixedSecrets.add(result.getSecrets().get(0));

        List<byte[]> mixedFactors = new ArrayList<>(testBlindingFactors);
        mixedFactors.add(result.getBlindingFactors().get(0));

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> builder.createBlindedMessages(mixedSecrets, mixedFactors, 1)
        );
        assertTrue(exception.getMessage().contains("same keyset ID"));
    }

    @Test
    void testCreateBlindedMessagesThrowsOnInvalidBlindingFactor() {
        // Given - invalid blinding factor (not 32 bytes)
        List<byte[]> invalidFactors = new ArrayList<>(testBlindingFactors);
        invalidFactors.set(0, new byte[16]);  // Wrong size

        // When/Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> builder.createBlindedMessages(testSecrets, invalidFactors, 1)
        );
        assertTrue(exception.getMessage().contains("32 bytes"));
    }

    @Test
    void testBuildRequestSucceeds() {
        // Given
        List<BlindedMessage> blindedMessages = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            1
        );

        // When
        PostRestoreRequest request = builder.buildRequest(blindedMessages);

        // Then
        assertNotNull(request);
        assertNotNull(request.getBlindedMessages());
        assertEquals(blindedMessages.size(), request.getBlindedMessages().size());
        assertEquals(blindedMessages, request.getBlindedMessages());
    }

    @Test
    void testBuildRequestThrowsOnEmptyBlindedMessages() {
        // Given
        List<BlindedMessage> emptyMessages = List.of();

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            builder.buildRequest(emptyMessages)
        );
    }

    @Test
    void testBuildRequestFromSecretsSucceeds() {
        // Given
        int amount = 2;

        // When
        PostRestoreRequest request = builder.buildRequestFromSecrets(
            testSecrets,
            testBlindingFactors,
            amount
        );

        // Then
        assertNotNull(request);
        assertNotNull(request.getBlindedMessages());
        assertEquals(testSecrets.size(), request.getBlindedMessages().size());

        // Verify all messages have correct amount
        for (BlindedMessage msg : request.getBlindedMessages()) {
            assertEquals(amount, msg.getAmount());
        }
    }

    @Test
    void testBuildRequestFromSecretsWithDifferentAmounts() {
        // Given
        int[] amounts = {1, 2, 4, 8, 16};

        for (int amount : amounts) {
            // When
            PostRestoreRequest request = builder.buildRequestFromSecrets(
                testSecrets,
                testBlindingFactors,
                amount
            );

            // Then
            for (BlindedMessage msg : request.getBlindedMessages()) {
                assertEquals(amount, msg.getAmount(),
                    "Amount should be " + amount + " sats");
            }
        }
    }

    @Test
    void testBuildRequestWithBatchOf100() {
        // Given - NUT-13 recommended batch size
        DeterministicKey masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, "");
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 100);
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // When
        PostRestoreRequest request = builder.buildRequestFromSecrets(
            result.getSecrets(),
            result.getBlindingFactors(),
            1
        );

        // Then
        assertEquals(100, request.getBlindedMessages().size());
    }

    @Test
    void testBlindedMessagesAreValidPublicKeys() {
        // Given
        int amount = 1;

        // When
        List<BlindedMessage> blindedMessages = builder.createBlindedMessages(
            testSecrets,
            testBlindingFactors,
            amount
        );

        // Then - each blinded message should have valid public key bytes
        for (BlindedMessage msg : blindedMessages) {
            byte[] pkBytes = msg.getBlindedMessage().getUncompressedBytes();
            assertNotNull(pkBytes);
            // Uncompressed public key should be 64 bytes (X and Y coordinates)
            assertEquals(64, pkBytes.length,
                "Blinded message public key should be 64 bytes (uncompressed without prefix)");
        }
    }
}
