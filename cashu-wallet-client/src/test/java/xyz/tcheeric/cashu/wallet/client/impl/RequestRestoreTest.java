package xyz.tcheeric.cashu.wallet.client.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testConstructorSucceeds() {
        // When
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then
        assertNotNull(request);
        assertEquals(TEST_MINT_URL, request.getBaseUrl());
        assertEquals("/restore", request.getPath());
        assertEquals("POST", request.getHttpMethod());
        assertEquals(testRequest, request.getRequestObject());
        assertEquals(PostRestoreResponse.class, request.getResponseType());
    }

    @Test
    void testConstructorThrowsOnNullUrl() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new RequestRestore(null, testRequest)
        );
    }

    @Test
    void testConstructorThrowsOnNullRequest() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
            new RequestRestore(TEST_MINT_URL, null)
        );
    }

    @Test
    void testInheritsFromAbstractRequestBase() {
        // Given
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then - should have execute() method from base class
        assertNotNull(request);
        assertTrue(request.getClass().getSuperclass().getSimpleName().contains("AbstractRequestBase"));
    }

    @Test
    void testUsesCorrectEndpoint() {
        // Given
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then
        assertEquals("/restore", request.getPath(),
            "Should use NUT-09 /restore endpoint");
    }

    @Test
    void testUsesPostMethod() {
        // Given
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then
        assertEquals("POST", request.getHttpMethod(),
            "Restore requests must use POST method");
    }

    @Test
    void testHasCorrectResponseType() {
        // Given
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then
        assertEquals(PostRestoreResponse.class, request.getResponseType(),
            "Response type should be PostRestoreResponse");
    }

    @Test
    void testWorksWithDifferentMintUrls() {
        // Given
        String[] mintUrls = {
            "https://mint1.example.com",
            "https://mint2.example.com:3338",
            "http://localhost:3338"
        };

        for (String url : mintUrls) {
            // When
            RequestRestore request = new RequestRestore(url, testRequest);

            // Then
            assertEquals(url, request.getBaseUrl());
        }
    }

    @Test
    void testConstructsRestTemplateAutomatically() {
        // Given
        RequestRestore request = new RequestRestore(TEST_MINT_URL, testRequest);

        // Then
        assertNotNull(request.getRestTemplate(),
            "RestTemplate should be automatically initialized");
    }
}
