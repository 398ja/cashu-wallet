package xyz.tcheeric.cashu.wallet.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintRequest;
import xyz.tcheeric.cashu.wallet.client.impl.RequestMintToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves a mint quote id never reaches the request log. A funded, unlocked quote id is a bearer
 * claim under NUT-04, so the log carries only its {@link QuoteRef}.
 */
class QuoteIdLoggingTest {

    private static final String BASE_URL = "https://mint.example.com";
    private static final String QUOTE_ID = "Xk9fQ2vT7wLmPz3aB8nR";
    private static final String STATUS_PATH = "/v1/mint/quote/bolt11/" + QUOTE_ID;

    private final Logger logger = (Logger) LoggerFactory.getLogger(AbstractRequestBase.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    private static class StatusRequest extends AbstractRequestBase<String, Void> {
        StatusRequest() {
            this(STATUS_PATH);
        }

        StatusRequest(String path) {
            super(BASE_URL, path, String.class);
        }
    }

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    /** A successful status poll logs the dispatch and the success with the ref, never the id. */
    @Test
    void shouldLogQuoteRefNotIdWhenStatusPollSucceeds() {
        // Arrange
        StatusRequest request = new StatusRequest();
        MockRestServiceServer server = MockRestServiceServer.createServer(request.getRestTemplate());
        server.expect(requestTo(BASE_URL + STATUS_PATH)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // Act
        request.execute();

        // Assert
        List<String> lines = loggedLines().stream().filter(line -> line.contains("target=")).toList();
        assertThat(lines).hasSize(2).allSatisfy(line -> assertThat(line)
                .doesNotContain(QUOTE_ID)
                .contains(QuoteRef.of(QUOTE_ID)));
    }

    /**
     * A failed status poll whose NUT-00 body echoes the id logs the ref, keeps the mint error
     * code, and still throws the original exception to the caller.
     */
    @Test
    void shouldLogQuoteRefAndMintCodeWhenStatusPollFails() {
        // Arrange
        StatusRequest request = new StatusRequest();
        MockRestServiceServer server = MockRestServiceServer.createServer(request.getRestTemplate());
        String body = "{\"detail\":\"quote " + QUOTE_ID + " is not paid\",\"code\":20001,\"quote\":\""
                + QUOTE_ID + "\"}";
        server.expect(requestTo(BASE_URL + STATUS_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(body).contentType(MediaType.APPLICATION_JSON));

        // Act
        assertThatThrownBy(request::execute).isInstanceOf(HttpClientErrorException.class);

        // Assert
        List<String> lines = loggedLines();
        assertThat(lines).isNotEmpty().allSatisfy(line -> assertThat(line).doesNotContain(QUOTE_ID));
        assertThat(lines.get(lines.size() - 1)).contains("code=20001").contains(QuoteRef.of(QUOTE_ID));
    }

    private List<String> loggedLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * A failed POST /mint carries the quote id only in its body. When the mint's NUT-00 detail
     * echoes it, the error line must still show only the ref.
     */
    @Test
    void shouldRedactBodyQuoteIdWhenMintPostFails() {
        // Arrange
        PostMintRequest<Secret> body = new PostMintRequest<>();
        body.setQuoteId(QUOTE_ID);
        RequestMintToken<Secret> request = new RequestMintToken<>(BASE_URL, PaymentMethod.BOLT11, body);
        MockRestServiceServer server = MockRestServiceServer.createServer(request.getRestTemplate());
        server.expect(requestTo(BASE_URL + "/mint/bolt11")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body(mintErrorEchoing(QUOTE_ID)).contentType(MediaType.APPLICATION_JSON));

        // Act
        assertThatThrownBy(request::execute).isInstanceOf(HttpClientErrorException.class);

        // Assert
        List<String> lines = loggedLines();
        assertThat(lines).isNotEmpty().allSatisfy(line -> assertThat(line).doesNotContain(QUOTE_ID));
        assertThat(lines.get(lines.size() - 1)).contains("code=20001").contains(QuoteRef.of(QUOTE_ID));
    }

    /** A status URL whose method segment is uppercase or hyphenated still logs only the ref. */
    @Test
    void shouldRedactIdWhenMethodSegmentIsUppercaseOrHyphenated() {
        // Arrange
        StatusRequest request = new StatusRequest("/v1/mint/quote/BOLT-12/" + QUOTE_ID);
        MockRestServiceServer server = MockRestServiceServer.createServer(request.getRestTemplate());
        server.expect(requestTo(BASE_URL + "/v1/mint/quote/BOLT-12/" + QUOTE_ID)).andRespond(
                withStatus(HttpStatus.BAD_REQUEST).body(mintErrorEchoing(QUOTE_ID)).contentType(MediaType.APPLICATION_JSON));

        // Act
        assertThatThrownBy(request::execute).isInstanceOf(HttpClientErrorException.class);

        // Assert
        assertThat(loggedLines()).anyMatch(line -> line.contains("request_failed"))
                .allSatisfy(line -> assertThat(line).doesNotContain(QUOTE_ID));
    }

    private static String mintErrorEchoing(String quoteId) {
        return "{\"detail\":\"quote " + quoteId + " is not paid\",\"code\":20001}";
    }
}
