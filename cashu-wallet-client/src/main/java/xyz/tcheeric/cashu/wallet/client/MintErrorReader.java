package xyz.tcheeric.cashu.wallet.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;
import xyz.tcheeric.cashu.common.nut00.CashuErrorCode;
import xyz.tcheeric.cashu.entities.rest.ErrorResponse;

import java.util.Optional;

/**
 * Reads the NUT-00 error body a mint returns alongside a failed HTTP response.
 *
 * <p>A mint reports failures as {@code {"detail": <string>, "code": <int>}}. Without reading it a
 * caller sees only an HTTP status and cannot tell "quote not paid yet, keep polling" from "quote
 * expired, stop", so this turns the body into a typed {@link CashuErrorCode} where one is present.
 *
 * <p>Parsing is best-effort by design. A mint that returns an empty body, HTML, or an unregistered
 * code still produced a real failure, and that failure must keep propagating unchanged rather than
 * being replaced by a parsing error.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/00.md">NUT-00</a>
 */
@Slf4j
final class MintErrorReader {

    private MintErrorReader() {
    }

    /**
     * Extracts the mint's error body from a failed request, when the failure carries one.
     *
     * @param failure the exception thrown by the HTTP client
     * @return the parsed error body, or empty when the failure carries no readable NUT-00 body
     */
    static Optional<ErrorResponse> read(Throwable failure) {
        if (!(failure instanceof RestClientResponseException responseException)) {
            return Optional.empty();
        }

        String body = responseException.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        return parse(body);
    }

    private static Optional<ErrorResponse> parse(String body) {
        try {
            return Optional.ofNullable(ErrorResponse.fromJson(body));
        } catch (RuntimeException e) {
            log.debug("mint_error body_not_nut00 reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Renders an error body as a single grep-friendly log fragment.
     *
     * <p>Only the registry code and the mint's own detail are rendered. Neither carries wallet
     * secrets, so no masking is required here.
     */
    static String describe(ErrorResponse error) {
        return String.format("code=%d error_code=%s detail=%s",
            error.code(),
            error.errorCode().map(CashuErrorCode::name).orElse("unregistered"),
            error.detail());
    }
}
