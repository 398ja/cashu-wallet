package xyz.tcheeric.cashu.wallet.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import xyz.tcheeric.cashu.common.nut00.CashuErrorCode;
import xyz.tcheeric.cashu.entities.rest.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MintErrorReader}, covering the NUT-00 error body a mint returns on failure.
 *
 * <p>A wallet that cannot read this body sees only an HTTP status, and so cannot tell a quote that
 * is merely unpaid from one that has expired.
 */
class MintErrorReaderTest {

    /**
     * Ensures a NUT-00 error body is parsed into its registry code, which is what lets a caller
     * decide between retrying and giving up.
     */
    @Test
    void shouldExposeTheRegistryCodeWhenMintReturnsANut00Body() {
        HttpClientErrorException failure = errorWithBody(
            HttpStatus.BAD_REQUEST, new ErrorResponse(CashuErrorCode.quote_expired).toJson());

        Optional<ErrorResponse> error = MintErrorReader.read(failure);

        assertThat(error).isPresent();
        assertThat(error.get().errorCode()).contains(CashuErrorCode.quote_expired);
    }

    /**
     * Ensures an unregistered numeric code still yields a readable detail rather than being
     * discarded, since the mint's own wording remains the only diagnostic available.
     */
    @Test
    void shouldRetainTheDetailWhenCodeIsNotInTheRegistry() {
        HttpClientErrorException failure = errorWithBody(
            HttpStatus.BAD_REQUEST, "{\"detail\":\"mint is on fire\",\"code\":987654}");

        Optional<ErrorResponse> error = MintErrorReader.read(failure);

        assertThat(error).isPresent();
        assertThat(error.get().detail()).isEqualTo("mint is on fire");
        assertThat(error.get().errorCode()).isEmpty();
        assertThat(MintErrorReader.describe(error.get())).contains("unregistered");
    }

    /**
     * Ensures a non-NUT-00 body is reported as absent rather than throwing.
     *
     * <p>A mint behind a proxy may answer with HTML. The underlying failure is still real and must
     * keep propagating, so a parsing problem must never replace it.
     */
    @Test
    void shouldReportNoErrorWhenBodyIsNotNut00() {
        HttpClientErrorException failure = errorWithBody(
            HttpStatus.BAD_GATEWAY, "<html><body>502 Bad Gateway</body></html>");

        assertThat(MintErrorReader.read(failure)).isEmpty();
    }

    /**
     * Ensures a failure that never reached the mint carries no error body.
     */
    @Test
    void shouldReportNoErrorWhenFailureCarriesNoResponse() {
        ResourceAccessException failure = new ResourceAccessException("connect timed out",
            new IOException("connect timed out"));

        assertThat(MintErrorReader.read(failure)).isEmpty();
    }

    private HttpClientErrorException errorWithBody(HttpStatus status, String body) {
        return HttpClientErrorException.create(
            status,
            status.getReasonPhrase(),
            org.springframework.http.HttpHeaders.EMPTY,
            body.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
    }
}
