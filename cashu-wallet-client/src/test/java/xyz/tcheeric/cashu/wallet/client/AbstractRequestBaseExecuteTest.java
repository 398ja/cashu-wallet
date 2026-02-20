package xyz.tcheeric.cashu.wallet.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link AbstractRequestBase#execute()} method.
 *
 * <p>These tests verify the HTTP execution behavior including:
 * <ul>
 *   <li>X-Request-ID header injection</li>
 *   <li>Success and failure logging paths</li>
 *   <li>RestTemplate configuration usage</li>
 * </ul>
 */
class AbstractRequestBaseExecuteTest {

    private static final String TEST_BASE_URL = "https://mint.example.com";
    private static final String TEST_PATH = "/test";

    /**
     * Concrete implementation of AbstractRequestBase for testing GET requests.
     */
    private static class TestGetRequest extends AbstractRequestBase<String, Void> {
        public TestGetRequest(String baseUrl, String path) {
            super(baseUrl, path, String.class);
        }
    }

    /**
     * Concrete implementation of AbstractRequestBase for testing POST requests.
     */
    private static class TestPostRequest extends AbstractRequestBase<String, String> {
        public TestPostRequest(String baseUrl, String path, String requestBody) {
            super(baseUrl, path, "POST", requestBody, String.class);
        }
    }

    @Test
    void shouldIncludeRequestIdHeaderOnGetRequest() {
        // Arrange
        TestGetRequest request = new TestGetRequest(TEST_BASE_URL, TEST_PATH);
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AbstractRequestBase.REQUEST_ID_HEADER, org.hamcrest.Matchers.matchesPattern("[a-f0-9]{8}")))
                .andRespond(withSuccess("response", MediaType.TEXT_PLAIN));

        // Act
        String result = request.execute();

        // Assert
        assertThat(result).isEqualTo("response");
        mockServer.verify();
    }

    @Test
    void shouldIncludeRequestIdHeaderOnPostRequest() {
        // Arrange
        TestPostRequest request = new TestPostRequest(TEST_BASE_URL, TEST_PATH, "{\"test\":\"data\"}");
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AbstractRequestBase.REQUEST_ID_HEADER, org.hamcrest.Matchers.matchesPattern("[a-f0-9]{8}")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("response", MediaType.TEXT_PLAIN));

        // Act
        String result = request.execute();

        // Assert
        assertThat(result).isEqualTo("response");
        mockServer.verify();
    }

    @Test
    void shouldReturnResponseBodyOnSuccessfulGetRequest() {
        // Arrange
        TestGetRequest request = new TestGetRequest(TEST_BASE_URL, TEST_PATH);
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        String expectedResponse = "successful response";
        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expectedResponse, MediaType.TEXT_PLAIN));

        // Act
        String result = request.execute();

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        mockServer.verify();
    }

    @Test
    void shouldReturnResponseBodyOnSuccessfulPostRequest() {
        // Arrange
        String requestBody = "{\"key\":\"value\"}";
        TestPostRequest request = new TestPostRequest(TEST_BASE_URL, TEST_PATH, requestBody);
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        String expectedResponse = "post response";
        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(requestBody))
                .andRespond(withSuccess(expectedResponse, MediaType.TEXT_PLAIN));

        // Act
        String result = request.execute();

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        mockServer.verify();
    }

    @Test
    void shouldPropagateExceptionOnGetRequestFailure() {
        // Arrange
        TestGetRequest request = new TestGetRequest(TEST_BASE_URL, TEST_PATH);
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Act & Assert
        assertThatThrownBy(request::execute)
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("404");
        mockServer.verify();
    }

    @Test
    void shouldPropagateExceptionOnPostRequestFailure() {
        // Arrange
        TestPostRequest request = new TestPostRequest(TEST_BASE_URL, TEST_PATH, "{}");
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        assertThatThrownBy(request::execute)
                .isInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("500");
        mockServer.verify();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForUnsupportedHttpMethod() {
        // Arrange - Create a request with unsupported HTTP method via reflection-like trick
        AbstractRequestBase<String, String> request = new AbstractRequestBase<>(
                TEST_BASE_URL, TEST_PATH, "DELETE", null, String.class) {};

        // Act & Assert
        assertThatThrownBy(request::execute)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HTTP method: DELETE");
    }

    @Test
    void shouldUseConfiguredRestTemplate() {
        // Arrange
        TestGetRequest request = new TestGetRequest(TEST_BASE_URL, TEST_PATH);

        // Act
        RestTemplate restTemplate = request.getRestTemplate();

        // Assert - Verify the RestTemplate is not null and is the shared instance
        assertThat(restTemplate).isNotNull();

        // Create another request and verify they share the same RestTemplate instance
        TestGetRequest anotherRequest = new TestGetRequest(TEST_BASE_URL, "/other");
        assertThat(anotherRequest.getRestTemplate()).isSameAs(restTemplate);
    }

    @Test
    void shouldSetContentTypeJsonForPostRequests() {
        // Arrange
        TestPostRequest request = new TestPostRequest(TEST_BASE_URL, TEST_PATH, "{\"data\":1}");
        RestTemplate restTemplate = request.getRestTemplate();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        mockServer.expect(requestTo(TEST_BASE_URL + TEST_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        // Act
        request.execute();

        // Assert
        mockServer.verify();
    }
}
