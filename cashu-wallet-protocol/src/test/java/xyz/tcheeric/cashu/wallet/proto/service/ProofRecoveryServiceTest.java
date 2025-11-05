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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for {@link ProofRecoveryServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ProofRecoveryServiceTest {

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
        KeysetId keysetId = KeysetId.fromString("009a1f293253e41e");
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
    }

    /**
     * Ensures the service returns an empty list when the mint responds without signatures.
     */
    @Test
    void shouldReturnEmptyListWhenNoBlindSignaturesProvided() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(List.of());

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).isEmpty();
        verify(bdkhUtils, never()).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures a null signatures list is treated as empty.
     */
    @Test
    void shouldReturnEmptyListWhenBlindSignaturesAreNull() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        response.setBlindSignatures(null);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).isEmpty();
    }

    /**
     * Verifies mismatched secret and blinding factor list sizes raise an error.
     */
    @Test
    void shouldThrowWhenSecretsAndBlindingFactorsHaveDifferentSizes() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<byte[]> tooFewFactors = testBlindingFactors.subList(0, 1);

        // Act & Assert
        assertThatThrownBy(() -> service.unblindAndCreateProofs(response, testSecrets, tooFewFactors, keySet))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mismatch");
    }

    /**
     * Ensures valid signatures are unblinded into proofs.
     */
    @Test
    void shouldUnblindSignaturesWhenInputIsValid() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(1)).thenReturn(mockPublicKey);  // Amount is 1 in createMockBlindSignatures

        // Mock unblinding - need valid 64-byte uncompressed signature (without prefix)
        // cashu-lib expects 64 bytes for uncompressed signatures (X and Y coordinates)
        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).hasSize(2);
        verify(bdkhUtils, times(2)).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures entries with invalid blinding factors are skipped while others succeed.
     */
    @Test
    void shouldSkipProofsWithInvalidBlindingFactor() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // Create invalid blinding factor (wrong size)
        List<byte[]> invalidFactors = new ArrayList<>(testBlindingFactors);
        invalidFactors.set(0, new byte[16]);  // Wrong size

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(1)).thenReturn(mockPublicKey);  // Amount is 1 in createMockBlindSignatures

        // Mock unblinding for valid factor
       byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            invalidFactors,
            keySet
        );

        // Assert - should skip first proof but process second
        assertThat(proofs).hasSize(1);
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures entries without a matching public key are skipped.
     */
    @Test
    void shouldSkipProofsWhenMintPublicKeyMissing() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(2);
        response.setBlindSignatures(blindSignatures);

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        // First call returns null (missing public key), second returns valid key
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(anyInt()))
            .thenReturn(null)
            .thenReturn(mockPublicKey);

        // Mock unblinding
        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).hasSize(1);
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures unblinding continues when an individual entry fails.
     */
    @Test
    void shouldContinueProcessingWhenUnblindingThrows() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(3);
        response.setBlindSignatures(blindSignatures);

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(1)).thenReturn(mockPublicKey);  // Amount is 1 in createMockBlindSignatures

        // Mock unblinding - first throws exception, others succeed
        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any()))
            .thenThrow(new RuntimeException("Test error"))
            .thenReturn(validSignature)
            .thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).hasSize(2);
        verify(bdkhUtils, times(3)).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures filterUnspentProofs is a no-op until spent checking is implemented.
     */
    @Test
    void shouldReturnAllProofsUntilSpentCheckImplemented() {
        // Arrange
        List<Proof<DeterministicSecret>> proofs = new ArrayList<>();
        for (DeterministicSecret secret : testSecrets) {
            proofs.add(Proof.<DeterministicSecret>builder()
                .secret(secret)
                .amount(1)
                .keySetId("009a1f293253e41e")
                .build());
        }

        // Act
        List<Proof<DeterministicSecret>> filtered = service.filterUnspentProofs(
            proofs,
            "https://mint.example.com"
        );

        // Assert
        assertThat(filtered).containsExactlyElementsOf(proofs);
    }

    /**
     * Confirms the default constructor wires the internal BDHKE utilities.
     */
    @Test
    void shouldCreateServiceWithDefaultUtilities() {
        // Act
        ProofRecoveryServiceImpl defaultService = new ProofRecoveryServiceImpl();

        // Assert
        assertThat(defaultService).isNotNull();
    }

    /**
     * Ensures partial responses are processed up to the number of signatures returned.
     */
    @Test
    void shouldHandlePartialRestoreResponses() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(1);  // Only 1 signature
        response.setBlindSignatures(blindSignatures);

        // Setup keySet mock
        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        // Mock public keys
        PublicKey mockPublicKey = mock(PublicKey.class);
        when(mockPublicKey.getBytes()).thenReturn(new byte[33]);
        when(keys.get(1)).thenReturn(mockPublicKey);  // Amount is 1 in createMockBlindSignatures

        // Mock unblinding
        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).hasSize(1);
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    // Helper methods

    private List<BlindSignature> createMockBlindSignatures(int count) {
        List<BlindSignature> signatures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlindSignature sig = mock(BlindSignature.class);
            lenient().when(sig.getAmount()).thenReturn(1);
            lenient().when(sig.getKeySetId()).thenReturn(KeysetId.fromString("009a1f293253e41e"));

            Signature blindedSig = mock(Signature.class);
            lenient().when(blindedSig.getBytes()).thenReturn(new byte[33]);
            lenient().when(sig.getBlindedSignature()).thenReturn(blindedSig);

            signatures.add(sig);
        }
        return signatures;
    }
}
