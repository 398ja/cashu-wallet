package xyz.tcheeric.cashu.wallet.client.service;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.impl.ProofRecoveryServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Simple integration tests for {@link ParallelRecoveryServiceImpl}.
 *
 * <p>These tests focus on the parallel execution functionality without deeply
 * mocking the recovery internals, which is already tested in WalletRecoveryServiceTest.
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
class ParallelRecoveryServiceSimpleTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_PASSPHRASE = "";
    private static final String TEST_MINT_URL = "https://mint.example.com";
    private static final String KEYSET_1 = "009a1f293253e41e";
    private static final String KEYSET_2 = "00b2c3d4e5f6a7b8";
    private static final String KEYSET_3 = "00c4d5e6f7a8b9c0";

    private ExecutorService executor;
    private ParallelRecoveryServiceImpl service;
    private DeterministicKey masterKey;
    private List<KeysetId> keysetIds;
    private List<KeySet> keySets;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(3);
        service = new ParallelRecoveryServiceImpl(TEST_MINT_URL);
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);

        keysetIds = List.of(
            KeysetId.fromString(KEYSET_1),
            KeysetId.fromString(KEYSET_2),
            KeysetId.fromString(KEYSET_3)
        );

        keySets = List.of(
            createKeySet(KEYSET_1),
            createKeySet(KEYSET_2),
            createKeySet(KEYSET_3)
        );
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // ===== Basic Parallel Functionality Tests =====

    @Test
    @DisplayName("Should return CompletableFuture when starting parallel recovery")
    void testReturnsCompletableFuture() {
        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(TEST_MNEMONIC, TEST_PASSPHRASE, keysetIds, keySets, executor);

        // Assert
        assertThat(future).isNotNull();
        assertThat(future).isInstanceOf(CompletableFuture.class);

        // Cancel to avoid waiting for completion
        future.cancel(true);
    }

    @Test
    @DisplayName("Should return CompletableFuture for single keyset async recovery")
    void testSingleKeysetAsyncReturnsCompletableFuture() {
        // Act
        CompletableFuture<List<Proof<DeterministicSecret>>> future =
            service.recoverKeysetAsync(
                masterKey,
                keysetIds.get(0),
                keySets.get(0),
                0,
                executor
            );

        // Assert
        assertThat(future).isNotNull();
        assertThat(future).isInstanceOf(CompletableFuture.class);

        // Cancel to avoid waiting for completion
        future.cancel(true);
    }

    @Test
    @DisplayName("Should allow custom batch size in async recovery")
    void testCustomBatchSizeAsync() {
        // Act
        CompletableFuture<List<Proof<DeterministicSecret>>> future =
            service.recoverKeysetAsync(
                masterKey,
                keysetIds.get(0),
                keySets.get(0),
                0,
                50,  // Custom batch size
                executor
            );

        // Assert
        assertThat(future).isNotNull();

        // Cancel to avoid waiting for completion
        future.cancel(true);
    }

    // ===== Validation Tests =====

    @Test
    @DisplayName("Should reject invalid mnemonic immediately")
    void testInvalidMnemonic() throws Exception {
        // Arrange
        String invalidMnemonic = "invalid mnemonic phrase";

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(invalidMnemonic, "", keysetIds, keySets, executor);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("Should reject empty keyset IDs list")
    void testEmptyKeysetIdsList() throws Exception {
        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(TEST_MNEMONIC, TEST_PASSPHRASE, List.of(), keySets, executor);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should reject empty KeySets list")
    void testEmptyKeySetsList() throws Exception {
        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(TEST_MNEMONIC, TEST_PASSPHRASE, keysetIds, List.of(), executor);

        // Assert
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    // ===== Instance Type Tests =====

    @Test
    @DisplayName("Should extend WalletRecoveryServiceImpl")
    void testExtendsWalletRecoveryService() {
        // Assert
        assertThat(service).isInstanceOf(WalletRecoveryServiceImpl.class);
    }

    @Test
    @DisplayName("Should implement ParallelRecoveryService interface")
    void testImplementsParallelRecoveryService() {
        // Assert
        assertThat(service).isInstanceOf(ParallelRecoveryService.class);
    }

    @Test
    @DisplayName("Should be able to create with default constructor")
    void testDefaultConstructor() {
        // Act
        ParallelRecoveryServiceImpl defaultService =
            new ParallelRecoveryServiceImpl("https://example.com");

        // Assert
        assertThat(defaultService).isNotNull();
    }

    @Test
    @DisplayName("Should be able to create with custom dependencies")
    void testCustomDependenciesConstructor() {
        // Act
        ParallelRecoveryServiceImpl customService = new ParallelRecoveryServiceImpl(
            TEST_MINT_URL,
            new RestoreRequestBuilder(),
            new ProofRecoveryServiceImpl()
        );

        // Assert
        assertThat(customService).isNotNull();
    }

    // ===== Helper Methods =====

    private KeySet createKeySet(String keysetId) {
        KeySet keySet = new KeySet();
        keySet.setId(keysetId);
        Keys keys = new Keys();
        // Add a mock public key for amount 1
        keys.put(java.math.BigInteger.ONE, PublicKey.fromBytes(createCompressedKey()));
        keySet.setKeys(keys);
        return keySet;
    }

    private byte[] createCompressedKey() {
        byte[] bytes = new byte[33];
        bytes[0] = 0x02;
        return bytes;
    }
}
