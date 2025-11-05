package xyz.tcheeric.cashu.wallet.proto.tasks;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DeriveSecretsTask}.
 *
 * <p>These tests verify the task's ability to derive deterministic secrets and blinding
 * factors using real cryptographic operations from bip-utils.
 */
class DeriveSecretsTaskIT {

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

    @Test
    void testExecuteDerivesCorrectNumberOfSecrets() {
        // Given
        int count = 10;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, count);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then
        assertNotNull(result);
        assertEquals(count, result.getSecrets().size());
        assertEquals(count, result.getBlindingFactors().size());
        assertEquals(count, result.getCount());
    }

    @Test
    void testExecuteCreatesSecretsWithMetadata() {
        // Given
        int startCounter = 5;
        int count = 3;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, startCounter, count);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then
        List<DeterministicSecret> secrets = result.getSecrets();
        for (int i = 0; i < count; i++) {
            DeterministicSecret secret = secrets.get(i);
            assertTrue(secret.hasMetadata());
            assertEquals(keysetId, secret.getKeysetId());
            assertEquals(startCounter + i, secret.getCounter());
            assertNotNull(secret.getDerivationPath());
        }
    }

    @Test
    void testExecuteIsDeterministic() {
        // Given
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 5);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // When
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Then - same inputs produce same outputs
        assertEquals(result1.getSecrets().size(), result2.getSecrets().size());
        for (int i = 0; i < result1.getSecrets().size(); i++) {
            assertArrayEquals(
                result1.getSecrets().get(i).getData(),
                result2.getSecrets().get(i).getData(),
                "Secret at index " + i + " should be identical"
            );
            assertArrayEquals(
                result1.getBlindingFactors().get(i),
                result2.getBlindingFactors().get(i),
                "Blinding factor at index " + i + " should be identical"
            );
        }
    }

    @Test
    void testExecuteWithDifferentCountersProducesDifferentSecrets() {
        // Given
        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId, 1, 1);

        // When
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Then - different counters produce different secrets
        assertFalse(
            java.util.Arrays.equals(
                result1.getSecrets().get(0).getData(),
                result2.getSecrets().get(0).getData()
            ),
            "Different counters should produce different secrets"
        );
    }

    @Test
    void testExecuteWithBatchOf100() {
        // Given - NUT-13 recommended batch size
        int batchSize = 100;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, batchSize);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then
        assertEquals(batchSize, result.getSecrets().size());
        assertEquals(batchSize, result.getBlindingFactors().size());
    }

    @Test
    void testBlindingFactorsAre32Bytes() {
        // Given
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then - all blinding factors should be 32 bytes
        for (int i = 0; i < result.getBlindingFactors().size(); i++) {
            byte[] blindingFactor = result.getBlindingFactors().get(i);
            assertNotNull(blindingFactor, "Blinding factor at index " + i + " should not be null");
            assertEquals(32, blindingFactor.length,
                "Blinding factor at index " + i + " should be 32 bytes");
        }
    }

    @Test
    void testSecretsAre32Bytes() {
        // Given
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then - all secrets should be 32 bytes
        for (int i = 0; i < result.getSecrets().size(); i++) {
            byte[] secret = result.getSecrets().get(i).getData();
            assertNotNull(secret, "Secret at index " + i + " should not be null");
            assertEquals(32, secret.length,
                "Secret at index " + i + " should be 32 bytes");
        }
    }

    @Test
    void testResultValidationPasses() {
        // Given
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then - validation should not throw
        assertDoesNotThrow(result::validate);
    }

    @Test
    void testExecuteWithLargeStartCounter() {
        // Given - test with large counter value
        int startCounter = 1000;
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, startCounter, 10);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then
        assertEquals(10, result.getSecrets().size());
        assertEquals(startCounter, result.getSecrets().get(0).getCounter());
        assertEquals(startCounter + 9, result.getSecrets().get(9).getCounter());
    }

    @Test
    void testSecretsHaveCorrectKeysetId() {
        // Given
        DeriveSecretsTask task = new DeriveSecretsTask(masterKey, keysetId, 0, 5);

        // When
        DeriveSecretsTask.DeriveSecretsResult result = task.execute();

        // Then - all secrets should have the same keyset ID
        for (DeterministicSecret secret : result.getSecrets()) {
            assertEquals(keysetId, secret.getKeysetId());
        }
    }

    @Test
    void testDifferentMnemonicsProduceDifferentSecrets() {
        // Given
        String mnemonic2 = "legal winner thank year wave sausage worth useful legal winner thank yellow";
        DeterministicKey masterKey2 = Bip39.mnemonicToMasterKey(mnemonic2, TEST_PASSPHRASE);

        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey2, keysetId, 0, 1);

        // When
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Then - different mnemonics produce different secrets
        assertFalse(
            java.util.Arrays.equals(
                result1.getSecrets().get(0).getData(),
                result2.getSecrets().get(0).getData()
            ),
            "Different mnemonics should produce different secrets"
        );
    }

    @Test
    void testDifferentKeysetsProduceDifferentSecrets() {
        // Given
        KeysetId keysetId2 = KeysetId.fromString("009a1f293253e41f");

        DeriveSecretsTask task1 = new DeriveSecretsTask(masterKey, keysetId, 0, 1);
        DeriveSecretsTask task2 = new DeriveSecretsTask(masterKey, keysetId2, 0, 1);

        // When
        DeriveSecretsTask.DeriveSecretsResult result1 = task1.execute();
        DeriveSecretsTask.DeriveSecretsResult result2 = task2.execute();

        // Then - different keysets produce different secrets
        assertFalse(
            java.util.Arrays.equals(
                result1.getSecrets().get(0).getData(),
                result2.getSecrets().get(0).getData()
            ),
            "Different keysets should produce different secrets"
        );
    }
}
