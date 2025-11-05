package xyz.tcheeric.cashu.wallet.client.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RequestRestore}.
 *
 * <p>Note: These are basic structural tests. Full integration tests require a running mint
 * and are covered in end-to-end tests.
 */
class RequestRestoreTest {

    private static final String TEST_MINT_URL = "https://mint.example.com";

    private PostRestoreRequest testRequest;

    @BeforeEach
    void setUp() {
        // Create a minimal test request
        testRequest = new PostRestoreRequest(List.of());
    }

    /**
     * Verifies that the constructor wires the mint URL, endpoint path, HTTP method,
     * request payload, and response type.
     */
    @Test
    void shouldExposeConfiguredRequestMetadataWhenConstructed() {
        // Arrange
        // Act
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getBaseUrl()).isEqualTo(TEST_MINT_URL);
        assertThat(request.getPath()).isEqualTo("/restore");
        assertThat(request.getHttpMethod()).isEqualTo("POST");
        assertThat(request.getRequestObject()).isEqualTo(testRequest);
        assertThat(request.getResponseType()).isEqualTo(PostRestoreResponse.class);
    }

    /**
     * Ensures a null mint URL fails fast before any request can be created.
     */
    @Test
    void shouldThrowWhenMintUrlIsNull() {
        // Arrange
        // Act & Assert
        assertThatThrownBy(() -> new RequestRestore(null, testRequest))
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Ensures a null restore payload is rejected because the call cannot proceed without it.
     */
    @Test
    void shouldThrowWhenRequestPayloadIsNull() {
        // Arrange
        // Act & Assert
        assertThatThrownBy(() -> new RequestRestore(TEST_MINT_URL, null))
            .isInstanceOf(NullPointerException.class);
    }

    /**
     * Confirms the client inherits the shared request base to reuse execution logic.
     */
    @Test
    void shouldExtendAbstractRequestBase() {
        // Arrange
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Act & Assert
        assertThat(request.getClass().getSuperclass().getSimpleName())
            .contains("AbstractRequestBase");
    }

    /**
     * Validates that the client targets the NUT-09 /restore endpoint.
     */
    @Test
    void shouldTargetRestoreEndpointWhenConstructed() {
        // Arrange
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Act & Assert
        assertThat(request.getPath()).isEqualTo("/restore");
    }

    /**
     * Ensures restore requests use POST as required by NUT-09.
     */
    @Test
    void shouldUsePostMethodForRestoreCalls() {
        // Arrange
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Act & Assert
        assertThat(request.getHttpMethod()).isEqualTo("POST");
    }

    /**
     * Confirms the response type is wired to the restore response DTO.
     */
    @Test
    void shouldExposePostRestoreResponseType() {
        // Arrange
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Act & Assert
        assertThat(request.getResponseType()).isEqualTo(PostRestoreResponse.class);
    }

    /**
     * Checks that arbitrary mint URLs are preserved without modification.
     */
    @Test
    void shouldStoreProvidedMintUrlWhenDifferentHostsUsed() {
        // Arrange
        String[] mintUrls = {
            "https://mint1.example.com",
            "https://mint2.example.com:3338",
            "http://localhost:3338"
        };

        // Act & Assert
        for (String url : mintUrls) {
            RequestRestore request = new RequestRestore(url, testRequest);

            // Assert
            assertThat(request.getBaseUrl()).isEqualTo(url);
        }
    }

    /**
     * Ensures the client has a ready-to-use RestTemplate instance.
     */
    @Test
    void shouldInitialiseRestTemplateAutomatically() {
        // Arrange
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Act & Assert
        assertThat(request.getRestTemplate()).isNotNull();
    }
}
