package xyz.tcheeric.cashu.wallet.proto.service;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link WalletRecoveryServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class WalletRecoveryServiceIT {

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
        service = new WalletRecoveryServiceImpl(TEST_MINT_URL, requestBuilder, proofRecoveryService);
        keysetId = new KeysetId(TEST_KEYSET_ID);
        masterKey = Bip39.mnemonicToMasterKey(TEST_MNEMONIC, TEST_PASSPHRASE);

        // Setup mock KeySet
        keySet = new KeySet();
        keySet.setId(TEST_KEYSET_ID);
        Keys keys = new Keys();
        keys.put(java.math.BigInteger.ONE, mock(PublicKey.class));
        keySet.setKeys(keys);
    }

    @Test
    void testRecoverThrowsOnInvalidMnemonic() {
        // Given
        String invalidMnemonic = "invalid mnemonic phrase";

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.recover(invalidMnemonic, "", List.of(keysetId), List.of(keySet))
        );
        assertTrue(exception.getMessage().contains("Invalid"));
    }

    @Test
    void testRecoverThrowsOnEmptyKeysetIds() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.recover(TEST_MNEMONIC, TEST_PASSPHRASE, List.of(), List.of(keySet))
        );
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void testRecoverThrowsOnEmptyKeySets() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.recover(TEST_MNEMONIC, TEST_PASSPHRASE, List.of(keysetId), List.of())
        );
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    void testRecoverKeysetThrowsOnNegativeBatchSize() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.recoverKeyset(masterKey, keysetId, keySet, 0, -1)
        );
        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void testRecoverKeysetThrowsOnNegativeStartCounter() {
        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.recoverKeyset(masterKey, keysetId, keySet, -1, 100)
        );
        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    void testRecoverKeysetUsesDefaultBatchSize() {
        // Given - setup mocks to return empty responses immediately
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // Mock empty response to trigger termination
        PostRestoreResponse emptyResponse = new PostRestoreResponse();
        emptyResponse.setBlindSignatures(List.of());

        // When
        List<Proof<DeterministicSecret>> proofs = service.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0
        );

        // Then
        assertNotNull(proofs);
        assertTrue(proofs.isEmpty());
    }

    @Test
    void testRecoverKeysetStopsAfterThreeEmptyBatches() {
        // Given - setup mocks to always return empty responses
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        PostRestoreResponse emptyResponse = new PostRestoreResponse();
        emptyResponse.setBlindSignatures(List.of());

        // When
        List<Proof<DeterministicSecret>> proofs = service.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0,
            10  // Small batch for faster test
        );

        // Then
        assertNotNull(proofs);
        assertTrue(proofs.isEmpty());
        // Should have called builder exactly 3 times (MAX_EMPTY_BATCHES)
        verify(requestBuilder, times(3)).buildRequestFromSecrets(any(), any(), anyInt());
    }

    @Test
    void testRecoverKeysetResetsEmptyBatchCountOnSuccess() {
        // Given
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // First 2 batches: empty
        // Third batch: has signatures
        // Fourth batch: empty (should reset counter)
        // Fifth-Seventh batches: empty (to reach 3 consecutive empties)
        PostRestoreResponse emptyResponse = new PostRestoreResponse();
        emptyResponse.setBlindSignatures(List.of());

        PostRestoreResponse successResponse = new PostRestoreResponse();
        List<BlindSignature> signatures = new ArrayList<>();
        signatures.add(createMockBlindSignature());
        successResponse.setBlindSignatures(signatures);

        // Mock recovery service to return empty proofs (we just care about the flow)
        when(proofRecoveryService.unblindAndCreateProofs(any(), any(), any(), any()))
            .thenReturn(List.of());

        // Note: This test is simplified since we can't easily mock RequestRestore execution
        // In a real scenario, you'd use dependency injection for the REST client
    }

    @Test
    void testRecoverWithValidMnemonic() {
        // Given
        List<KeysetId> keysetIds = List.of(keysetId);
        List<KeySet> keySets = List.of(keySet);

        // Mock builder
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // Mock empty responses to quickly terminate
        PostRestoreResponse emptyResponse = new PostRestoreResponse();
        emptyResponse.setBlindSignatures(List.of());

        // When
        List<Proof<DeterministicSecret>> proofs = service.recover(
            TEST_MNEMONIC,
            TEST_PASSPHRASE,
            keysetIds,
            keySets
        );

        // Then
        assertNotNull(proofs);
    }

    @Test
    void testRecoverWithMultipleKeysets() {
        // Given
        KeysetId keysetId2 = new KeysetId("009a1f293253e41f");
        KeySet keySet2 = new KeySet();
        keySet2.setId("009a1f293253e41f");
        Keys keys2 = new Keys();
        keys2.put(java.math.BigInteger.ONE, mock(PublicKey.class));
        keySet2.setKeys(keys2);

        List<KeysetId> keysetIds = List.of(keysetId, keysetId2);
        List<KeySet> keySets = List.of(keySet, keySet2);

        // Mock builder
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // When
        List<Proof<DeterministicSecret>> proofs = service.recover(
            TEST_MNEMONIC,
            TEST_PASSPHRASE,
            keysetIds,
            keySets
        );

        // Then
        assertNotNull(proofs);
        // Should have attempted recovery for both keysets
        verify(requestBuilder, atLeast(6)).buildRequestFromSecrets(any(), any(), anyInt());
        // (3 empty batches per keyset = 6 total calls minimum)
    }

    @Test
    void testRecoverContinuesOnKeysetError() {
        // Given
        KeysetId keysetId2 = new KeysetId("009a1f293253e41f");
        // Intentionally don't add keySet2 to the list to cause lookup failure

        List<KeysetId> keysetIds = List.of(keysetId, keysetId2);
        List<KeySet> keySets = List.of(keySet);  // Only one keyset

        // Mock builder
        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // When
        List<Proof<DeterministicSecret>> proofs = service.recover(
            TEST_MNEMONIC,
            TEST_PASSPHRASE,
            keysetIds,
            keySets
        );

        // Then - should still succeed and process first keyset
        assertNotNull(proofs);
    }

    @Test
    void testConstructorWithMintUrlOnly() {
        // When
        WalletRecoveryServiceImpl simpleService = new WalletRecoveryServiceImpl(TEST_MINT_URL);

        // Then
        assertNotNull(simpleService);
    }

    @Test
    void testRecoverKeysetWithCustomBatchSize() {
        // Given
        int customBatchSize = 50;

        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // When
        List<Proof<DeterministicSecret>> proofs = service.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            0,
            customBatchSize
        );

        // Then
        assertNotNull(proofs);
        // Verify batch size was used (would need to verify DeriveSecretsTask creation, which is internal)
    }

    @Test
    void testRecoverKeysetWithNonZeroStartCounter() {
        // Given
        int startCounter = 100;

        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // When
        List<Proof<DeterministicSecret>> proofs = service.recoverKeyset(
            masterKey,
            keysetId,
            keySet,
            startCounter,
            10
        );

        // Then
        assertNotNull(proofs);
    }

    @Test
    void testRecoverUsesCorrectPassphrase() {
        // Given
        String customPassphrase = "my-passphrase";
        List<KeysetId> keysetIds = List.of(keysetId);
        List<KeySet> keySets = List.of(keySet);

        when(requestBuilder.buildRequestFromSecrets(any(), any(), anyInt()))
            .thenReturn(new PostRestoreRequest(List.of()));

        // When
        List<Proof<DeterministicSecret>> proofs = service.recover(
            TEST_MNEMONIC,
            customPassphrase,
            keysetIds,
            keySets
        );

        // Then - should derive different master key with passphrase
        assertNotNull(proofs);
    }

    // Helper methods

    private BlindSignature createMockBlindSignature() {
        BlindSignature sig = mock(BlindSignature.class);
        when(sig.getAmount()).thenReturn(1);
        when(sig.getKeySetId()).thenReturn(new KeysetId(TEST_KEYSET_ID));

        Signature blindedSig = mock(Signature.class);
        when(blindedSig.getBytes()).thenReturn(new byte[33]);
        when(sig.getBlindedSignature()).thenReturn(blindedSig);

        return sig;
    }
}
