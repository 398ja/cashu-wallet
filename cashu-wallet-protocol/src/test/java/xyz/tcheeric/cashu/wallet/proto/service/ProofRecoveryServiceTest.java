package xyz.tcheeric.cashu.wallet.proto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.*;
import xyz.tcheeric.cashu.common.nut12.DLEQProof;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateRequest;
import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateResponse;
import xyz.tcheeric.cashu.entities.rest.nut09.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.proto.service.impl.ProofRecoveryServiceImpl;

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
    private DLEQVerificationService dleqVerificationService;

    @Mock
    private CheckStateClient checkStateClient;

    @Mock
    private KeySet keySet;

    @Mock
    private Keys keys;

    private ProofRecoveryServiceImpl service;
    private List<DeterministicSecret> testSecrets;
    private List<byte[]> testBlindingFactors;

    @BeforeEach
    void setUp() {
        service = new ProofRecoveryServiceImpl(bdkhUtils, dleqVerificationService, checkStateClient);

        lenient().when(dleqVerificationService.verifyBlindSignature(any(), any(), any())).thenReturn(true);
        lenient().when(dleqVerificationService.verifyProof(any(), any())).thenReturn(true);
        lenient().when(dleqVerificationService.addDLEQToProof(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(checkStateClient.checkState(anyString(), any())).thenReturn(new PostCheckStateResponse());

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
        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
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
        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
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
        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
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
        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
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
        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
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

    /**
     * Ensures spent-check filters out proofs marked as spent by the mint.
     */
    @Test
    void shouldFilterOutSpentProofs() {
        // Arrange
        Proof<DeterministicSecret> proof1 = Proof.<DeterministicSecret>builder()
            .amount(1)
            .secret(testSecrets.get(0))
            .keySetId("009a1f293253e41e")
            .unblindedSignature(Signature.fromBytes(createCompressedKey()))
            .build();
        Proof<DeterministicSecret> proof2 = Proof.<DeterministicSecret>builder()
            .amount(1)
            .secret(testSecrets.get(1))
            .keySetId("009a1f293253e41e")
            .unblindedSignature(Signature.fromBytes(createCompressedKey()))
            .build();

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state1 = new PostCheckStateResponse.ResponseState();
        state1.setHashToCurveSecret(new HashToCurveSecret(proof1));
        state1.setState("UNSPENT");
        PostCheckStateResponse.ResponseState state2 = new PostCheckStateResponse.ResponseState();
        state2.setHashToCurveSecret(new HashToCurveSecret(proof2));
        state2.setState("SPENT");
        response.setStates(List.of(state1, state2));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        List<Proof<DeterministicSecret>> filtered = service.filterUnspentProofs(
            List.of(proof1, proof2),
            "https://mint.example.com"
        );

        // Assert
        assertThat(filtered).containsExactly(proof1);
        verify(checkStateClient).checkState(eq("https://mint.example.com"), any());
    }

    /**
     * Ensures DLEQ verification hooks are invoked when blind signatures include DLEQ data.
     */
    @Test
    void shouldInvokeDleqVerificationWhenProofContainsDleq() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        BlindSignature blindSignature = mock(BlindSignature.class);
        Signature blindedSig = Signature.fromBytes(createCompressedKey());
        String eHex = "1".repeat(64);
        String sHex = "2".repeat(64);
        String rHex = "3".repeat(64);
        DLEQProof dleqProof = DLEQProof.forBlindSignature(eHex, sHex);

        lenient().when(blindSignature.getAmount()).thenReturn(1);
        lenient().when(blindSignature.getKeySetId()).thenReturn(KeysetId.fromString("009a1f293253e41e"));
        lenient().when(blindSignature.getBlindedSignature()).thenReturn(blindedSig);
        when(blindSignature.hasDLEQProof()).thenReturn(true);
        when(blindSignature.getDleq()).thenReturn(dleqProof);

        response.setBlindSignatures(List.of(blindSignature));

        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
        when(keys.get(1)).thenReturn(mockPublicKey);

        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);
        when(dleqVerificationService.verifyBlindSignature(any(), any(), any())).thenReturn(true);
        when(dleqVerificationService.verifyProof(any(), any())).thenReturn(true);
        when(dleqVerificationService.addDLEQToProof(any(), any(), any()))
            .thenAnswer(invocation -> {
                Proof<DeterministicSecret> proof = invocation.getArgument(0);
                proof.setDleq(DLEQProof.forProof(eHex, sHex, rHex));
                return proof;
            });

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert
        assertThat(proofs).hasSize(1);
        verify(dleqVerificationService).verifyBlindSignature(eq(blindSignature), any(PublicKey.class), eq(mockPublicKey));
        verify(dleqVerificationService).addDLEQToProof(any(), eq(testBlindingFactors.get(0)), eq(dleqProof));
        verify(dleqVerificationService).verifyProof(any(), eq(mockPublicKey));
    }

    // ============================================
    // Tests for verifyProofsUnspent (P1-WALLET-002)
    // ============================================

    /**
     * Ensures verifyProofsUnspent returns empty result for empty input list.
     */
    @Test
    void verifyProofsUnspent_shouldReturnEmptyResultForEmptyList() {
        // Act
        ProofStateVerificationResult result = service.verifyProofsUnspent(
            List.of(),
            "https://mint.example.com"
        );

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.unspentProofs()).isEmpty();
        assertThat(result.spentProofs()).isEmpty();
        assertThat(result.unknownProofs()).isEmpty();
        assertThat(result.allUnspent()).isTrue();
        verifyNoInteractions(checkStateClient);
    }

    /**
     * Ensures verifyProofsUnspent correctly categorizes all unspent proofs.
     */
    @Test
    void verifyProofsUnspent_shouldCategorizeAllUnspentProofs() {
        // Arrange
        List<Proof<DeterministicSecret>> proofs = createTestProofs(3);

        PostCheckStateResponse response = new PostCheckStateResponse();
        List<PostCheckStateResponse.ResponseState> states = proofs.stream()
            .map(proof -> {
                PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
                state.setHashToCurveSecret(new HashToCurveSecret(proof));
                state.setState("UNSPENT");
                return state;
            })
            .toList();
        response.setStates(states);

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        ProofStateVerificationResult result = service.verifyProofsUnspent(
            proofs,
            "https://mint.example.com"
        );

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.unspentProofs()).hasSize(3);
        assertThat(result.spentProofs()).isEmpty();
        assertThat(result.unknownProofs()).isEmpty();
        assertThat(result.allUnspent()).isTrue();
        assertThat(result.hasSpentProofs()).isFalse();
    }

    /**
     * Ensures verifyProofsUnspent correctly categorizes mixed states.
     */
    @Test
    void verifyProofsUnspent_shouldCategorizesMixedStates() {
        // Arrange
        List<Proof<DeterministicSecret>> proofs = createTestProofs(3);

        PostCheckStateResponse response = new PostCheckStateResponse();
        List<PostCheckStateResponse.ResponseState> states = new ArrayList<>();

        // First proof: UNSPENT
        PostCheckStateResponse.ResponseState state1 = new PostCheckStateResponse.ResponseState();
        state1.setHashToCurveSecret(new HashToCurveSecret(proofs.get(0)));
        state1.setState("UNSPENT");
        states.add(state1);

        // Second proof: SPENT
        PostCheckStateResponse.ResponseState state2 = new PostCheckStateResponse.ResponseState();
        state2.setHashToCurveSecret(new HashToCurveSecret(proofs.get(1)));
        state2.setState("SPENT");
        states.add(state2);

        // Third proof: numeric UNSPENT (0)
        PostCheckStateResponse.ResponseState state3 = new PostCheckStateResponse.ResponseState();
        state3.setHashToCurveSecret(new HashToCurveSecret(proofs.get(2)));
        state3.setState("0");
        states.add(state3);

        response.setStates(states);
        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        ProofStateVerificationResult result = service.verifyProofsUnspent(
            proofs,
            "https://mint.example.com"
        );

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.unspentProofs()).hasSize(2);
        assertThat(result.spentProofs()).hasSize(1);
        assertThat(result.unknownProofs()).isEmpty();
        assertThat(result.allUnspent()).isFalse();
        assertThat(result.hasSpentProofs()).isTrue();
    }

    /**
     * Ensures verifyProofsUnspent handles network errors gracefully.
     */
    @Test
    void verifyProofsUnspent_shouldReturnFailureOnNetworkError() {
        // Arrange
        List<Proof<DeterministicSecret>> proofs = createTestProofs(2);
        when(checkStateClient.checkState(anyString(), any()))
            .thenThrow(new RuntimeException("Network timeout"));

        // Act
        ProofStateVerificationResult result = service.verifyProofsUnspent(
            proofs,
            "https://mint.example.com"
        );

        // Assert
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Network timeout");
        assertThat(result.unknownProofs()).hasSize(2);
        assertThat(result.unspentProofs()).isEmpty();
        assertThat(result.spentProofs()).isEmpty();
    }

    /**
     * Ensures verifyProofsUnspent handles proofs with missing state in response.
     */
    @Test
    void verifyProofsUnspent_shouldMarkMissingStatesAsUnknown() {
        // Arrange
        List<Proof<DeterministicSecret>> proofs = createTestProofs(2);

        // Response only contains state for first proof
        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state1 = new PostCheckStateResponse.ResponseState();
        state1.setHashToCurveSecret(new HashToCurveSecret(proofs.get(0)));
        state1.setState("UNSPENT");
        response.setStates(List.of(state1));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        ProofStateVerificationResult result = service.verifyProofsUnspent(
            proofs,
            "https://mint.example.com"
        );

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.unspentProofs()).hasSize(1);
        assertThat(result.unknownProofs()).hasSize(1);
        assertThat(result.hasUnknownProofs()).isTrue();
    }

    // ============================================
    // Tests for canSafelyDelete (P1-WALLET-003)
    // ============================================

    /**
     * Ensures canSafelyDelete allows deletion of spent proofs.
     */
    @Test
    void canSafelyDelete_shouldAllowDeletionOfSpentProof() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
        state.setHashToCurveSecret(new HashToCurveSecret(proof));
        state.setState("SPENT");
        response.setStates(List.of(state));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isTrue();
        assertThat(result.isSpent()).isTrue();
        assertThat(result.verificationSucceeded()).isTrue();
        assertThat(result.reason()).contains("safe to delete");
    }

    /**
     * Ensures canSafelyDelete allows deletion with numeric spent state (1).
     */
    @Test
    void canSafelyDelete_shouldAllowDeletionWithNumericSpentState() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
        state.setHashToCurveSecret(new HashToCurveSecret(proof));
        state.setState("1"); // Numeric SPENT
        response.setStates(List.of(state));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isTrue();
        assertThat(result.isSpent()).isTrue();
    }

    /**
     * Ensures canSafelyDelete prevents deletion of unspent proofs.
     */
    @Test
    void canSafelyDelete_shouldPreventDeletionOfUnspentProof() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
        state.setHashToCurveSecret(new HashToCurveSecret(proof));
        state.setState("UNSPENT");
        response.setStates(List.of(state));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isFalse();
        assertThat(result.isUnspent()).isTrue();
        assertThat(result.verificationSucceeded()).isTrue();
        assertThat(result.reason()).contains("loss of funds");
    }

    /**
     * Ensures canSafelyDelete prevents deletion of pending proofs.
     */
    @Test
    void canSafelyDelete_shouldPreventDeletionOfPendingProof() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
        state.setHashToCurveSecret(new HashToCurveSecret(proof));
        state.setState("PENDING");
        response.setStates(List.of(state));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isFalse();
        assertThat(result.isPending()).isTrue();
        assertThat(result.verificationSucceeded()).isTrue();
        assertThat(result.reason()).contains("wait for state to settle");
    }

    /**
     * Ensures canSafelyDelete prevents deletion when state is missing.
     */
    @Test
    void canSafelyDelete_shouldPreventDeletionWhenStateMissing() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        response.setStates(List.of()); // Empty states

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isFalse();
        assertThat(result.verificationSucceeded()).isTrue();
        assertThat(result.reason()).contains("unknown or unrecognized");
    }

    /**
     * Ensures canSafelyDelete prevents deletion on network error.
     */
    @Test
    void canSafelyDelete_shouldPreventDeletionOnNetworkError() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);
        when(checkStateClient.checkState(anyString(), any()))
            .thenThrow(new RuntimeException("Connection refused"));

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isFalse();
        assertThat(result.verificationSucceeded()).isFalse();
        assertThat(result.reason()).contains("verification failed");
        assertThat(result.reason()).contains("Connection refused");
    }

    /**
     * Ensures canSafelyDelete handles unrecognized state values conservatively.
     */
    @Test
    void canSafelyDelete_shouldPreventDeletionOnUnrecognizedState() {
        // Arrange
        Proof<DeterministicSecret> proof = createTestProofs(1).get(0);

        PostCheckStateResponse response = new PostCheckStateResponse();
        PostCheckStateResponse.ResponseState state = new PostCheckStateResponse.ResponseState();
        state.setHashToCurveSecret(new HashToCurveSecret(proof));
        state.setState("INVALID_STATE");
        response.setStates(List.of(state));

        when(checkStateClient.checkState(anyString(), any())).thenReturn(response);

        // Act
        SafeDeleteResult result = service.canSafelyDelete(proof, "https://mint.example.com");

        // Assert
        assertThat(result.canDelete()).isFalse();
        assertThat(result.reason()).contains("unknown or unrecognized");
    }

    // ============================================
    // Tests for SW-06: Mint response validation
    // ============================================

    /**
     * Ensures oversized blind signature response is truncated to secrets size.
     */
    @Test
    void shouldWarnAndTruncateOversizedSignatureResponse() {
        // Arrange - 5 signatures but only 3 secrets
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = createMockBlindSignatures(5);
        response.setBlindSignatures(blindSignatures);

        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
        when(keys.get(1)).thenReturn(mockPublicKey);

        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert - should only process up to secrets.size() (3)
        assertThat(proofs).hasSize(3);
        verify(bdkhUtils, times(3)).unblindSignature(any(), any(), any());
    }

    /**
     * Ensures signatures with mismatched keyset ID are skipped.
     */
    @Test
    void shouldSkipSignatureWithMismatchedKeysetId() {
        // Arrange
        PostRestoreResponse response = new PostRestoreResponse();
        List<BlindSignature> blindSignatures = new ArrayList<>();

        // First signature with wrong keyset
        BlindSignature wrongKeyset = mock(BlindSignature.class);
        lenient().when(wrongKeyset.getAmount()).thenReturn(1);
        when(wrongKeyset.getKeySetId()).thenReturn(KeysetId.fromString("aaaaaaaaaaaaaaaa"));
        lenient().when(wrongKeyset.getBlindedSignature()).thenReturn(Signature.fromBytes(createCompressedKey()));
        blindSignatures.add(wrongKeyset);

        // Second signature with correct keyset
        BlindSignature correctKeyset = mock(BlindSignature.class);
        lenient().when(correctKeyset.getAmount()).thenReturn(1);
        when(correctKeyset.getKeySetId()).thenReturn(KeysetId.fromString("009a1f293253e41e"));
        lenient().when(correctKeyset.getBlindedSignature()).thenReturn(Signature.fromBytes(createCompressedKey()));
        blindSignatures.add(correctKeyset);

        response.setBlindSignatures(blindSignatures);

        when(keySet.getId()).thenReturn("009a1f293253e41e");
        when(keySet.getKeys()).thenReturn(keys);

        PublicKey mockPublicKey = PublicKey.fromBytes(createCompressedKey());
        when(keys.get(1)).thenReturn(mockPublicKey);

        byte[] validSignature = new byte[64];
        when(bdkhUtils.unblindSignature(any(), any(), any())).thenReturn(validSignature);

        // Act
        List<Proof<DeterministicSecret>> proofs = service.unblindAndCreateProofs(
            response,
            testSecrets,
            testBlindingFactors,
            keySet
        );

        // Assert - first signature should be skipped
        assertThat(proofs).hasSize(1);
        verify(bdkhUtils, times(1)).unblindSignature(any(), any(), any());
    }

    // Helper methods

    /**
     * Creates test proofs for verification tests.
     */
    private List<Proof<DeterministicSecret>> createTestProofs(int count) {
        List<Proof<DeterministicSecret>> proofs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            proofs.add(Proof.<DeterministicSecret>builder()
                .amount(1)
                .secret(testSecrets.get(i % testSecrets.size()))
                .keySetId("009a1f293253e41e")
                .unblindedSignature(Signature.fromBytes(createCompressedKey()))
                .build());
        }
        return proofs;
    }

    private List<BlindSignature> createMockBlindSignatures(int count) {
        List<BlindSignature> signatures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlindSignature sig = mock(BlindSignature.class);
            lenient().when(sig.getAmount()).thenReturn(1);
            lenient().when(sig.getKeySetId()).thenReturn(KeysetId.fromString("009a1f293253e41e"));

            Signature blindedSig = Signature.fromBytes(createCompressedKey());
            lenient().when(sig.getBlindedSignature()).thenReturn(blindedSig);

            signatures.add(sig);
        }
        return signatures;
    }

    private byte[] createCompressedKey() {
        byte[] bytes = new byte[33];
        bytes[0] = 0x02;
        return bytes;
    }
}
