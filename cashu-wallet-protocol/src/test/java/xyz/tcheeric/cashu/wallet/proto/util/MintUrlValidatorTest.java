package xyz.tcheeric.cashu.wallet.proto.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MintUrlValidator}.
 */
class MintUrlValidatorTest {

    @Test
    void shouldAcceptValidHttpsUrl() {
        assertThatCode(() -> MintUrlValidator.validate("https://mint.example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpsUrlWithPort() {
        assertThatCode(() -> MintUrlValidator.validate("https://mint.example.com:3338"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpsUrlWithPath() {
        assertThatCode(() -> MintUrlValidator.validate("https://mint.example.com/v1"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpLocalhost() {
        assertThatCode(() -> MintUrlValidator.validate("http://localhost:3338"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttp127001() {
        assertThatCode(() -> MintUrlValidator.validate("http://127.0.0.1:3338"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> MintUrlValidator.validate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or empty");
    }

    @Test
    void shouldRejectEmptyString() {
        assertThatThrownBy(() -> MintUrlValidator.validate(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or empty");
    }

    @Test
    void shouldRejectFileScheme() {
        assertThatThrownBy(() -> MintUrlValidator.validate("file:///etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectFtpScheme() {
        assertThatThrownBy(() -> MintUrlValidator.validate("ftp://mint.example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheme");
    }

    @Test
    void shouldRejectHttpForRemoteHost() {
        assertThatThrownBy(() -> MintUrlValidator.validate("http://mint.example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("localhost");
    }

    @Test
    void shouldRejectUserinfo() {
        assertThatThrownBy(() -> MintUrlValidator.validate("https://user:pass@mint.example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userinfo");
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThatThrownBy(() -> MintUrlValidator.validate("https://mint.example.com/../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void shouldRejectEmptyHost() {
        assertThatThrownBy(() -> MintUrlValidator.validate("https:///path"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host");
    }

    @Test
    void shouldNormalizeValidUrl() {
        String normalized = MintUrlValidator.validateAndNormalize("HTTPS://Mint.Example.COM:3338/");
        assertThat(normalized).isEqualTo("https://mint.example.com:3338");
    }

    @Test
    void shouldNormalizeAndStripTrailingSlash() {
        String normalized = MintUrlValidator.validateAndNormalize("https://mint.example.com/");
        assertThat(normalized).isEqualTo("https://mint.example.com");
    }

    @Test
    void validateAndNormalize_shouldRejectInvalidUrl() {
        assertThatThrownBy(() -> MintUrlValidator.validateAndNormalize("ftp://evil.com"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Edge cases from code review ---

    @Test
    void shouldRejectUrlEncodedPathTraversal() {
        assertThatThrownBy(() -> MintUrlValidator.validate("https://mint.example.com/%2e%2e/etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void shouldAcceptHttp127002AsLocalhost() {
        assertThatCode(() -> MintUrlValidator.validate("http://127.0.0.2:3338"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpIpv6Localhost() {
        assertThatCode(() -> MintUrlValidator.validate("http://[::1]:3338"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectHttp127NotInLoopback() {
        // 128.x.x.x is not a loopback address
        assertThatThrownBy(() -> MintUrlValidator.validate("http://128.0.0.1:3338"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("localhost");
    }

    @Test
    void shouldAcceptHttpsUrlWithQueryParams() {
        assertThatCode(() -> MintUrlValidator.validate("https://mint.example.com/v1?param=value"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpsUrlWithFragment() {
        assertThatCode(() -> MintUrlValidator.validate("https://mint.example.com/v1#section"))
            .doesNotThrowAnyException();
    }
}
