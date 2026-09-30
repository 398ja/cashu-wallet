package xyz.tcheeric.cashu.wallet.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link QuoteRef}, which must match imani-wallet-lib's ref byte for byte. */
class QuoteRefTest {

    /** The ref is q: and the first 6 bytes of SHA-256 of the id ("abc" is a known vector). */
    @Test
    void shouldHashIdToSixByteHexRefWhenIdGiven() {
        // Act
        String ref = QuoteRef.of("abc");

        // Assert
        assertThat(ref).isEqualTo("q:ba7816bf8f01");
    }

    /** A missing id renders as a placeholder rather than failing. */
    @Test
    void shouldRenderPlaceholderWhenIdMissing() {
        // Act and Assert
        assertThat(QuoteRef.of(null)).isEqualTo("(none)");
        assertThat(QuoteRef.of("")).isEqualTo("(none)");
    }

    /** Each known shape is redacted, and redacting twice leaves the refs alone. */
    @Test
    void shouldRedactEveryShapeOnceWhenTextCarriesIds() {
        // Arrange
        String text = "https://m/v1/melt/quote/bolt11/abc quote_id=abc {\"quote\":\"abc\"}";

        // Act
        String once = QuoteRef.redact(text);

        // Assert
        assertThat(once).doesNotContain("/abc").doesNotContain("=abc").doesNotContain("\"abc\"");
        assertThat(QuoteRef.redact(once)).isEqualTo(once);
    }

    /** Voucher status URLs keep the method segment and lose only the id. */
    @Test
    void shouldKeepMethodSegmentWhenVoucherUrlRedacted() {
        // Act
        String redacted = QuoteRef.redact("/v1/mint/quote/voucher/bolt11/abc");

        // Assert
        assertThat(redacted).isEqualTo("/v1/mint/quote/voucher/bolt11/q:ba7816bf8f01");
    }
}
