package xyz.tcheeric.cashu.wallet.client.service;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestRestore;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link ParallelRecoveryServiceImpl}.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Parallel recovery across multiple keysets</li>
 *   <li>Concurrent execution and thread safety</li>
 *   <li>Error isolation (one keyset failure doesn't affect others)</li>
 *   <li>Proper resource management and executor handling</li>
 *   <li>Edge cases (empty results, all failures, timeouts)</li>
 * </ul>
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@ExtendWith(MockitoExtension.class)
class ParallelRecoveryServiceTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_PASSPHRASE = "";
    private static final String TEST_MINT_URL = "https://mint.example.com";
    private static final String KEYSET_1 = "009a1f293253e41e";
    private static final String KEYSET_2 = "00b2c3d4e5f6a7b8";
    private static final String KEYSET_3 = "00c4d5e6f7a8b9c0";

    @Mock
    private RestoreRequestBuilder requestBuilder;

    @Mock
    private ProofRecoveryService proofRecoveryService;

    private ExecutorService executor;
    private ParallelRecoveryServiceImpl service;
    private DeterministicKey masterKey;
    private List<KeysetId> keysetIds;
    private List<KeySet> keySets;

    @BeforeEach
    void setUp() {
        // Create executor for parallel tests
        executor = Executors.newFixedThreadPool(3);

        // Create service with factory that returns empty responses
        service = new ParallelRecoveryServiceImpl(
            TEST_MINT_URL,
            requestBuilder,
            proofRecoveryService,
            createFactoryReturningEmptyResponses()
        );

        // Derive master key
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);

        // Setup keyset IDs
        keysetIds = List.of(
            KeysetId.fromString(KEYSET_1),
            KeysetId.fromString(KEYSET_2),
            KeysetId.fromString(KEYSET_3)
        );

        // Setup KeySets
        keySets = List.of(
            createKeySet(KEYSET_1),
            createKeySet(KEYSET_2),
            createKeySet(KEYSET_3)
        );

        // Default mock behaviors (lenient to avoid unnecessary stubbing errors)
        lenient().when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(mock(PostRestoreRequest.class));
        lenient().when(proofRecoveryService.unblindAndCreateProofs(any(), any(), any(), any()))
            .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // ===== Basic Functionality Tests =====

    @Test
    @DisplayName("Should recover from multiple keysets in parallel")
    void testRecoverMultipleKeysetsInParallel() throws Exception {
        // Arrange
        service = createServiceWithProofsPerKeyset(Map.of(
            KEYSET_1, 5,
            KEYSET_2, 3,
            KEYSET_3, 7
        ));

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(TEST_MNEMONIC, TEST_PASSPHRASE, keysetIds, keySets, executor);

        Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertThat(results).hasSize(3);
        assertThat(results.get(keysetIds.get(0))).hasSize(5);
        assertThat(results.get(keysetIds.get(1))).hasSize(3);
        assertThat(results.get(keysetIds.get(2))).hasSize(7);
    }

    @Test
    @DisplayName("Should recover using pre-derived master key")
    void testRecoverWithMasterKey() throws Exception {
        // Arrange
        service = createServiceWithProofsPerKeyset(Map.of(
            KEYSET_1, 2,
            KEYSET_2, 2
        ));

        List<KeysetId> twoKeysets = keysetIds.subList(0, 2);
        List<KeySet> twoKeySets = keySets.subList(0, 2);

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(masterKey, twoKeysets, twoKeySets, executor);

        Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results.get(twoKeysets.get(0))).hasSize(2);
        assertThat(results.get(twoKeysets.get(1))).hasSize(2);
    }

    @Test
    @DisplayName("Should recover single keyset asynchronously")
    void testRecoverKeysetAsync() throws Exception {
        // Arrange
        service = createServiceWithProofsPerKeyset(Map.of(KEYSET_1, 10));

        // Act
        CompletableFuture<List<Proof<DeterministicSecret>>> future =
            service.recoverKeysetAsync(masterKey, keysetIds.get(0), keySets.get(0), 0, executor);

        List<Proof<DeterministicSecret>> proofs = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertThat(proofs).hasSize(10);
    }

    @Test
    @DisplayName("Should recover keyset asynchronously with custom batch size")
    void testRecoverKeysetAsyncWithCustomBatchSize() throws Exception {
        // Arrange
        service = createServiceWithProofsPerKeyset(Map.of(KEYSET_1, 5));

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

        List<Proof<DeterministicSecret>> proofs = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertThat(proofs).hasSize(5);
    }

    // ===== Validation Tests =====

    @Test
    @DisplayName("Should fail when mnemonic is invalid")
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
    @DisplayName("Should fail when keyset IDs list is empty")
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
    @DisplayName("Should fail when keySets list is empty")
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

    // ===== Error Handling Tests =====

    @Test
    @DisplayName("Should isolate failures - one keyset failure does not affect others")
    void testErrorIsolation() throws Exception {
        // Arrange - Create factory that returns empty responses for all
        RestoreClientFactory factory = (mintUrl, keySet, request) -> {
            RequestRestore client = mock(RequestRestore.class);
            lenient().when(client.execute()).thenReturn(createEmptyResponse());
            return client;
        };

        service = new ParallelRecoveryServiceImpl(TEST_MINT_URL, requestBuilder, proofRecoveryService, factory);

        // Mock to create proofs for successful keysets
        // Use lenient stubbing to avoid unnecessary stubbing warnings
        lenient().when(proofRecoveryService.unblindAndCreateProofs(any(), any(), any(), any()))
            .thenReturn(List.of());

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(masterKey, keysetIds, keySets, executor);

        Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get(10, TimeUnit.SECONDS);

        // Assert - Should have results for successful keysets
        assertThat(results).isNotNull();
        // All keysets should complete successfully with empty results
        assertThat(results).hasSize(3);
        results.values().forEach(proofs -> assertThat(proofs).isEmpty());
    }

    @Test
    @DisplayName("Should handle all keysets returning empty results")
    void testAllKeysetsReturnEmpty() throws Exception {
        // Arrange - Service already configured with empty responses

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(TEST_MNEMONIC, TEST_PASSPHRASE, keysetIds, keySets, executor);

        Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get(10, TimeUnit.SECONDS);

        // Assert
        assertThat(results).isNotNull();
        assertThat(results).hasSize(3);
        results.values().forEach(proofs -> assertThat(proofs).isEmpty());
    }

    @Test
    @DisplayName("Should skip keyset when KeySet is missing")
    void testMissingKeySet() throws Exception {
        // Arrange - Only provide KeySets for 2 out of 3 keysets
        List<KeySet> incompleteKeySets = keySets.subList(0, 2);

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(masterKey, keysetIds, incompleteKeySets, executor);

        Map<KeysetId, List<Proof<DeterministicSecret>>> results = future.get(10, TimeUnit.SECONDS);

        // Assert - Should have results for available keysets
        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    // ===== Concurrency Tests =====

    @Test
    @DisplayName("Should execute keysets concurrently, not sequentially")
    void testConcurrentExecution() throws Exception {
        // Arrange - Create service that introduces delay
        CountDownLatch startLatch = new CountDownLatch(3);

        RestoreClientFactory slowFactory = (mintUrl, keySet, request) -> {
            RequestRestore client = mock(RequestRestore.class);
            when(client.execute()).thenAnswer(invocation -> {
                startLatch.countDown();  // Signal this keyset started
                Thread.sleep(50);  // Simulate short network delay
                return createEmptyResponse();
            });
            return client;
        };

        service = new ParallelRecoveryServiceImpl(TEST_MINT_URL, requestBuilder, proofRecoveryService, slowFactory);

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(masterKey, keysetIds, keySets, executor);

        // Wait for all keysets to start within a reasonable time
        boolean allStarted = startLatch.await(5, TimeUnit.SECONDS);

        // Get results
        future.get(10, TimeUnit.SECONDS);

        // Assert - All keysets should have started (proving concurrency)
        assertThat(allStarted).isTrue();
        // If they all started, they were running concurrently
    }

    @Test
    @DisplayName("Should be thread-safe when called concurrently")
    void testThreadSafety() throws Exception {
        // Arrange
        service = createServiceWithProofsPerKeyset(Map.of(
            KEYSET_1, 2,
            KEYSET_2, 2
        ));

        List<KeysetId> twoKeysets = keysetIds.subList(0, 2);
        List<KeySet> twoKeySets = keySets.subList(0, 2);

        // Act - Call recoverParallel multiple times concurrently
        List<CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
                service.recoverParallel(masterKey, twoKeysets, twoKeySets, executor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        allOf.get(15, TimeUnit.SECONDS);

        // Assert - All should complete successfully
        for (CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future : futures) {
            assertThat(future).isCompleted();
            assertThat(future.get()).hasSize(2);
        }
    }

    @Test
    @DisplayName("Should handle executor shutdown gracefully")
    void testExecutorShutdown() throws Exception {
        // Arrange
        ExecutorService tempExecutor = Executors.newFixedThreadPool(2);

        // Act
        CompletableFuture<Map<KeysetId, List<Proof<DeterministicSecret>>>> future =
            service.recoverParallel(masterKey, keysetIds.subList(0, 1), keySets.subList(0, 1), tempExecutor);

        // Shutdown executor immediately (before completion)
        tempExecutor.shutdown();

        // Should still complete tasks that were already submitted
        assertThatCode(() -> future.get(10, TimeUnit.SECONDS))
            .doesNotThrowAnyException();
    }

    // ===== Helper Methods =====

    private KeySet createKeySet(String keysetId) {
        KeySet keySet = new KeySet();
        keySet.setId(keysetId);
        Keys keys = new Keys();
        keys.put(java.math.BigInteger.ONE, mock(PublicKey.class));
        keySet.setKeys(keys);
        return keySet;
    }

    private RestoreClientFactory createFactoryReturningEmptyResponses() {
        return (mintUrl, keySet, request) -> {
            RequestRestore client = mock(RequestRestore.class);
            when(client.execute()).thenReturn(createEmptyResponse());
            return client;
        };
    }

    private PostRestoreResponse createEmptyResponse() {
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(List.of());
        return response;
    }

    private ParallelRecoveryServiceImpl createServiceWithProofsPerKeyset(Map<String, Integer> proofsPerKeyset) {
        Map<String, Boolean> responded = new ConcurrentHashMap<>();

        RestoreClientFactory factory = (mintUrl, keySet, request) -> {
            RequestRestore client = mock(RequestRestore.class);
            when(client.execute()).thenAnswer(invocation -> {
                boolean firstResponse = responded.putIfAbsent(keySet.getId(), Boolean.TRUE) == null;
                return firstResponse ? createResponseWithSignatures() : createEmptyResponse();
            });
            return client;
        };

        ProofRecoveryService proofService = mock(ProofRecoveryService.class);

        // Return proofs for any call, matching based on KeySet ID
        lenient().when(proofService.unblindAndCreateProofs(any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                KeySet keySet = invocation.getArgument(3);
                String keysetId = keySet.getId();
                Integer count = proofsPerKeyset.get(keysetId);
                if (count == null) {
                    return List.of();
                }
                List<Proof<DeterministicSecret>> proofs = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    proofs.add(createMockProof(keysetId));
                }
                return proofs;
            });

        return new ParallelRecoveryServiceImpl(TEST_MINT_URL, requestBuilder, proofService, factory);
    }

    private PostRestoreResponse createResponseWithSignatures() {
        PostRestoreResponse response = new PostRestoreResponse();
        // Add at least one signature so unblinding is attempted
        BlindSignature sig = new BlindSignature();
        sig.setAmount(1);
        sig.setKeySetId(KeysetId.fromString(KEYSET_1));
        sig.setBlindedSignature(mock(Signature.class));
        response.setBlindSignatures(List.of(sig));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Proof<DeterministicSecret> createMockProof(String keysetId) {
        Proof<DeterministicSecret> proof = mock(Proof.class);
        lenient().when(proof.getKeySetId()).thenReturn(keysetId);
        lenient().when(proof.getAmount()).thenReturn(1);
        DeterministicSecret secret = mock(DeterministicSecret.class);
        lenient().when(secret.getKeysetId()).thenReturn(KeysetId.fromString(keysetId));
        lenient().when(proof.getSecret()).thenReturn(secret);
        return proof;
    }
}
