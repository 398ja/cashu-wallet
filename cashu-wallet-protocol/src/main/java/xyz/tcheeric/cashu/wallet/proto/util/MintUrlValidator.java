package xyz.tcheeric.cashu.wallet.proto.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates and normalizes mint URLs to enforce trust boundaries.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Scheme must be {@code https} (or {@code http} only for localhost/127.0.0.0&#47;8/[::1])</li>
 *   <li>No userinfo (e.g., {@code user:pass@host})</li>
 *   <li>No path traversal segments ({@code ..})</li>
 *   <li>Non-empty host</li>
 * </ul>
 *
 * @see <a href="https://www.oracle.com/java/technologies/javase/seccodeguide.html">Oracle Secure Coding Guidelines</a>
 */
public final class MintUrlValidator {

    private MintUrlValidator() {
        throw new AssertionError("MintUrlValidator is a utility class and should not be instantiated");
    }

    /**
     * Validates a mint URL, throwing if it violates trust boundary rules.
     *
     * @param mintUrl the URL to validate
     * @throws IllegalArgumentException if the URL is null, empty, or violates validation rules
     */
    public static void validate(String mintUrl) {
        if (mintUrl == null || mintUrl.isBlank()) {
            throw new IllegalArgumentException("Mint URL must not be null or empty");
        }

        URI uri;
        try {
            uri = new URI(mintUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Mint URL is malformed: " + sanitize(mintUrl), e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Mint URL must have a scheme (https or http)");
        }
        scheme = scheme.toLowerCase();

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Mint URL must have a non-empty host");
        }

        if ("https".equals(scheme)) {
            // always allowed
        } else if ("http".equals(scheme)) {
            if (!isLocalhost(host)) {
                throw new IllegalArgumentException(
                    "HTTP scheme is only allowed for localhost/127.0.0.0/8/[::1]; use HTTPS for remote mints"
                );
            }
        } else {
            throw new IllegalArgumentException(
                "Mint URL scheme must be https (or http for localhost only); got: " + scheme
            );
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Mint URL must not contain userinfo (credentials in URL)");
        }

        // Check the original path for traversal segments before normalization resolves them
        String path = uri.getPath();
        if (path != null && path.contains("..")) {
            throw new IllegalArgumentException("Mint URL must not contain path traversal segments");
        }

        // Also check the raw path for encoded traversal sequences (%2e%2e or mixed variants)
        String rawPath = uri.getRawPath();
        if (rawPath != null && rawPath.toLowerCase().contains("%2e%2e")) {
            throw new IllegalArgumentException("Mint URL must not contain encoded path traversal segments");
        }
    }

    /**
     * Validates and normalizes a mint URL by reconstructing it from parsed URI components.
     *
     * <p>Note: Query parameters and fragments are intentionally stripped during normalization,
     * as mint base URLs should not contain these components. Only scheme, host, port, and path
     * are preserved.
     *
     * @param mintUrl the URL to validate and normalize
     * @return the normalized URL string (without query or fragment)
     * @throws IllegalArgumentException if the URL violates validation rules
     */
    public static String validateAndNormalize(String mintUrl) {
        validate(mintUrl);

        URI uri;
        try {
            uri = new URI(mintUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Mint URL is malformed: " + sanitize(mintUrl), e);
        }

        // Reconstruct from parsed components to normalize
        try {
            URI normalized = new URI(
                uri.getScheme().toLowerCase(),
                null, // no userinfo
                uri.getHost().toLowerCase(),
                uri.getPort(),
                uri.getPath(),
                null, // no query
                null  // no fragment
            );
            String result = normalized.toString();
            // Remove trailing slash for consistency
            if (result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Failed to normalize mint URL", e);
        }
    }

    private static boolean isLocalhost(String host) {
        String lower = host.toLowerCase();
        return "localhost".equals(lower)
            || lower.startsWith("127.")
            || "::1".equals(lower)
            || "[::1]".equals(lower);
    }

    private static String sanitize(String input) {
        if (input.length() > 100) {
            return input.substring(0, 100) + "...";
        }
        return input;
    }
}
