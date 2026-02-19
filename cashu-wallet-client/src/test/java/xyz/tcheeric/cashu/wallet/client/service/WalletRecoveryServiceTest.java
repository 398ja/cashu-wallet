package xyz.tcheeric.cashu.wallet.client.service;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.entities.rest.nut09.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.nut09.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.client.impl.RequestRestore;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;
import xyz.tcheeric.cashu.wallet.proto.service.ProofRecoveryService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link WalletRecoveryServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class WalletRecoveryServiceTest {

    private static final String TEST_MNEMONIC = "abandon abandon abandon abandon abandon abandon " +
                                                "abandon abandon abandon abandon abandon about";
    private static final String TEST_PASSPHRASE = "";
    private static final String TEST_MINT_URL = "https://mint.example.com";
    private static final String TEST_KEYSET_ID = "009a1f293253e41e";

    @Mock
    private RestoreRequestBuilder requestBuilder;

    @Mock
    private ProofRecoveryService proofRecoveryService;

    private WalletRecoveryServiceImpl service;
    private KeysetId keysetId;
    private KeySet keySet;
    private DeterministicKey masterKey;

    @BeforeEach
    void setUp() {
        service = serviceWithFactory(factoryReturningResponses(List.of()));
        keysetId = KeysetId.fromString(TEST_KEYSET_ID);
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);

        // Setup mock KeySet
        keySet = new KeySet();
        keySet.setId(TEST_KEYSET_ID);
        Keys keys = new Keys();
        keys.put(java.math.BigInteger.ONE, PublicKey.fromBytes(createCompressedKey()));
        keySet.setKeys(keys);
    }

    /**
     * Ensures recover rejects a mnemonic that fails BIP39 validation.
     */
    @Test
    void shouldThrowWhenMnemonicIsInvalid() {
        // Arrange
        String invalidMnemonic = "invalid mnemonic phrase";

        // Act & Assert
        assertThatThrownBy(() -> service.recover(invalidMnemonic, "", List.of(keysetId), List.of(keySet)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid");
    }

    /**
     * Ensures recover fails fast when no keyset IDs are provided.
     */
    @Test
    void shouldThrowWhenKeysetIdsListIsEmpty() {
        // Act & Assert
        assertThatThrownBy(() -> service.recover(TEST_MNEMONIC, TEST_PASSPHRASE, List.of(), List.of(keySet)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    /**
     * Ensures recover fails when no keyset metadata is supplied.
     */
    @Test
    void shouldThrowWhenKeySetsCollectionIsEmpty() {
        // Act & Assert
        assertThatThrownBy(() -> service.recover(TEST_MNEMONIC, TEST_PASSPHRASE, List.of(keysetId), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    /**
     * Ensures recoverKeyset rejects a non-positive batch size.
     */
    @Test
    void shouldThrowWhenBatchSizeIsNotPositive() {
        // Act & Assert
        assertThatThrownBy(() -> service.recoverKeyset(masterKey, keysetId, keySet, 0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
    }

    /**
     * Ensures recoverKeyset rejects a negative starting counter.
     */
    @Test
    void shouldThrowWhenStartCounterIsNegative() {
        // Act & Assert
        assertThatThrownBy(() -> service.recoverKeyset(masterKey, keysetId, keySet, -1, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    /**
     * Ensures the default batch size is used when callers rely on the overload without
     * providing a custom value.
     */
    @Test
    void shouldUseDefaultBatchSizeWhenRecoveringKeyset() {
        // Arrange
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));
        WalletRecoveryServiceImpl localService = serviceWithFactory(factoryReturningResponses(List.of()));

        // Act
        List<Proof<DeterministicSecret>> proofs = localService.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0
        );

        // Assert
        assertThat(proofs).isEmpty();
        ArgumentCaptor<List<DeterministicSecret>> secretsCaptor = ArgumentCaptor.forClass(List.class);
        verify(requestBuilder, atLeastOnce()).buildRequestFromSecrets(secretsCaptor.capture(), any(), anyInt());
        assertThat(secretsCaptor.getAllValues().get(0)).hasSize(WalletRecoveryService.DEFAULT_BATCH_SIZE);
    }

    /**
     * Verifies the recovery loop halts after three consecutive empty responses.
     */
    @Test
    void shouldStopAfterThreeConsecutiveEmptyBatches() {
        // Arrange
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        WalletRecoveryServiceImpl localService = serviceWithFactory(
            factoryReturningResponses(List.of(emptyResponse(), emptyResponse(), emptyResponse()))
        );

        // Act
        List<Proof<DeterministicSecret>> proofs = localService.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0,
            10
        );

        // Assert
        assertThat(proofs).isEmpty();
        verify(requestBuilder, times(WalletRecoveryService.MAX_EMPTY_BATCHES))
            .buildRequestFromSecrets(any(), any(), anyInt());
    }

    /**
     * Ensures wallet recovery keeps processing remaining keysets when one is missing.
     */
    @Test
    void shouldContinueRecoveringOtherKeysetsWhenOneIsMissing() {
        // Arrange
        KeysetId missingKeysetId = KeysetId.fromString("009a1f293253e41f");
        List<KeysetId> keysetIds = List.of(keysetId, missingKeysetId);
        List<KeySet> keySets = List.of(keySet);

        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        WalletRecoveryServiceImpl localService = serviceWithFactory(factoryReturningResponses(List.of()));

        // Act
        List<Proof<DeterministicSecret>> proofs = localService.recover(
            TEST_MNEMONIC,
            TEST_PASSPHRASE,
            keysetIds,
            keySets
        );

        // Assert
        assertThat(proofs).isEmpty();
    }

    /**
     * Verifies the convenience constructor wires default collaborators.
     */
    @Test
    void shouldCreateServiceWhenOnlyMintUrlProvided() {
        // Act
        WalletRecoveryServiceImpl simpleService = new WalletRecoveryServiceImpl(TEST_MINT_URL);

        // Assert
        assertThat(simpleService).isNotNull();
    }

    /**
     * Ensures a custom batch size is propagated to the request builder.
     */
    @Test
    void shouldPropagateCustomBatchSizeWhenProvided() {
        // Arrange
        int customBatchSize = 50;
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        WalletRecoveryServiceImpl localService = serviceWithFactory(factoryReturningResponses(List.of()));

        // Act
        List<Proof<DeterministicSecret>> proofs = localService.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0,
            customBatchSize
        );

        // Assert
        assertThat(proofs).isEmpty();
        ArgumentCaptor<List<DeterministicSecret>> secretsCaptor = ArgumentCaptor.forClass(List.class);
        verify(requestBuilder, atLeastOnce()).buildRequestFromSecrets(secretsCaptor.capture(), any(), anyInt());
        assertThat(secretsCaptor.getAllValues().get(0)).hasSize(customBatchSize);
    }

    /**
     * Verifies recovery loop terminates when max counter is reached even if
     * mint keeps returning non-empty responses.
     */
    @Test
    void shouldStopRecoveryWhenMaxCounterReached() {
        // Arrange - factory always returns non-empty response
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        PostRestoreResponse nonEmptyResponse = new PostRestoreResponse();
        BlindSignature sig = mock(BlindSignature.class);
        lenient().when(sig.getAmount()).thenReturn(1);
        nonEmptyResponse.setBlindSignatures(List.of(sig));

        when(proofRecoveryService.unblindAndCreateProofs(any(), any(), any(), any()))
            .thenReturn(List.of());

        RestoreClientFactory alwaysNonEmpty = (url, ks, req) -> new RequestRestore(url, req) {
            @Override
            public PostRestoreResponse execute() {
                return nonEmptyResponse;
            }
        };

        WalletRecoveryServiceImpl localService = serviceWithFactory(alwaysNonEmpty);

        // Act - start at a counter near the limit to avoid long test
        int nearLimit = WalletRecoveryService.MAX_COUNTER - 500;
        List<Proof<DeterministicSecret>> proofs = localService.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            nearLimit,
            100
        );

        // Assert - should have stopped at or near MAX_COUNTER, not run forever
        assertThat(proofs).isEmpty();
    }

    private WalletRecoveryServiceImpl serviceWithFactory(RestoreClientFactory factory) {
        return new WalletRecoveryServiceImpl(TEST_MINT_URL, requestBuilder, proofRecoveryService, factory);
    }

    private RestoreClientFactory factoryReturningResponses(List<PostRestoreResponse> responses) {
        Deque<PostRestoreResponse> queue = new ArrayDeque<>(responses);
        return (mintUrl, keySet, request) -> new RequestRestore(mintUrl, request) {
            @Override
            public PostRestoreResponse execute() {
                if (queue.isEmpty()) {
                    return emptyResponse();
                }
                return queue.removeFirst();
            }
        };
    }

    private PostRestoreResponse emptyResponse() {
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(List.of());
        return response;
    }

    private byte[] createCompressedKey() {
        byte[] bytes = new byte[33];
        bytes[0] = 0x02;
        return bytes;
    }
}
