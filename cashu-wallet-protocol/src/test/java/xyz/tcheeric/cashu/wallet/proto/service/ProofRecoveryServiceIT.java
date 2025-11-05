package xyz.tcheeric.cashu.wallet.proto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link ProofRecoveryServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ProofRecoveryServiceIT {

    @Mock
    private BDHKEUtilsService bdkhUtils;

    @Mock
    private KeySet keySet;

    @Mock
    private Keys keys;

    private ProofRecoveryServiceImpl service;
    private List<DeterministicSecret> testSecrets;
    private List<byte[]> testBlindingFactors;

    @BeforeEach
    void setUp() {
        service = new ProofRecoveryServiceImpl(bdkhUtils);

        // Setup test data
        KeysetId keysetId = new KeysetId("009a1f293253e41e");
        testSecrets = new ArrayList<>();
        testBlindingFactors = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            byte[] secretBytes = new byte[32];
            secretBytes[0] = (byte) i;
            DeterministicSecret secret = DeterministicSecret.create(secretBytes, keysetId, i);
            testSecrets.add(secret);

            byte[] blindingFactor = new byte[32];
            blindingFactor[0] = (byte) (i + 10);
            testBlindingFactors.add(blindingFactor);
        }

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);
    }

    @Test
    void testUnblindAndCreateProofsWithEmptyResponse() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(List.of());

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then
        assertNotNull(proofs);
        assertTrue(proofs.isEmpty());
        verify(bdkhUtils, never()).unblindSignature(any(), any(), any());
    }

    @Test
    void testUnblindAndCreateProofsWithNullSignatures() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(null);

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then
        assertNotNull(proofs);
        assertTrue(proofs.isEmpty());
    }

    @Test
    void testUnblindAndCreateProofsThrowsOnSizeMismatch() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        List<byte[]> tooFewFactors = testBlindingFactors.subList(0, 1);

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.unblindAndCreateProofs(response, testSecrets, tooFewFactors, keySet)
        );
        assertTrue(exception.getMessage().contains("mismatch"));
    }

    @Test
    void testUnblindAndCreateProofsWithValidSignatures() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt())).thenReturn(mockPublicKey);

        // Mock unblinding
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(new byte[33]);

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then
        assertNotNull(proofs);
        assertEquals(2, proofs.size());
        verify(bdkhUtils, times(2)).unblindSignature(any(), any(), any());
    }

    @Test
    void testUnblindAndCreateProofsSkipsInvalidBlindingFactor() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // Create invalid blinding factor (wrong size)
        List<byte[]> invalidFactors = new ArrayList<>(testBlindingFactors);
        invalidFactors.set(0, new byte[16]);  // Wrong size

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt())).thenReturn(mockPublicKey);

        // Mock unblinding for valid factor
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(new byte[33]);

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            invalidFactors,
            keySet
        );

        // Then - should skip first proof but process second
        assertNotNull(proofs);
        assertEquals(1, proofs.size());  // Only one valid proof
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    @Test
    void testUnblindAndCreateProofsSkipsMissingPublicKey() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // First call returns null (missing public key), second returns valid key
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt()))
            .thenReturn(null)
            .thenReturn(mockPublicKey);

        // Mock unblinding
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(new byte[33]);

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then - should skip first proof but process second
        assertNotNull(proofs);
        assertEquals(1, proofs.size());
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    @Test
    void testUnblindAndCreateProofsContinuesOnError() {
        // Given
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(3);
        response.setBlindSignatures(blindSignatures);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt())).thenReturn(mockPublicKey);

        // Mock unblinding - first throws exception, others succeed
        when(bdkhUtils.unblindSignature(any(), any(), any()))
            .thenThrow(new RuntimeException("Test error"))
            .thenReturn(new byte[33])
            .thenReturn(new byte[33]);

        // When
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then - should recover 2 proofs despite first failing
        assertNotNull(proofs);
        assertEquals(2, proofs.size());
        verify(bdkhUtils, times(3)).unblindSignature(any(), any(), any());
    }

    @Test
    void testFilterUnspentProofsReturnsAllProofs() {
        // Given - not yet implemented, should return all proofs
        List<Proof<DeterministicSecret>> proofs = new ArrayList<>();
        for (DeterministicSecret secret : testSecrets) {
            proofs.add(Proof.<DeterministicSecret>builder()
                .secret(secret)
                .amount(1)
                .keySetId("009a1f293253e41e")
                .build());
        }

        // When
        List<Proof<DeterministicSecret>> filtered = service.filterUnspentProofs(
            proofs,
            "https://mint.example.com"
        );

        // Then - not implemented, returns all
        assertEquals(proofs.size(), filtered.size());
        assertEquals(proofs, filtered);
    }

    @Test
    void testConstructorWithDefaultUtils() {
        // When
        ProofRecoveryServiceImpl defaultService = new ProofRecoveryServiceImpl();

        // Then
        assertNotNull(defaultService);
    }

    @Test
    void testUnblindAndCreateProofsHandlesPartialResponse() {
        // Given - response has fewer signatures than secrets sent
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(1);  // Only 1 signature
        response.setBlindSignatures(blindSignatures);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt())).thenReturn(mockPublicKey);

        // Mock unblinding
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(new byte[33]);

        // When - sent 3 secrets but only got 1 signature back
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Then - should recover only 1 proof
        assertNotNull(proofs);
        assertEquals(1, proofs.size());
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    // Helper methods

    private List<BlindSignature> createMockBlindSignatures(int count) {
        List<BlindSignature> signatures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlindSignature sig = mock(BlindSignature.class);
            when(sig.getAmount()).thenReturn(1);
            when(sig.getKeySetId()).thenReturn(new KeysetId("009a1f293253e41e"));

            Signature blindedSig = mock(Signature.class);
            when(blindedSig.getBytes()).thenReturn(new byte[33]);
            when(sig.getBlindedSignature()).thenReturn(blindedSig);

            signatures.add(sig);
        }
        return signatures;
    }
}
