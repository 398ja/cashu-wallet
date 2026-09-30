package xyz.tcheeric.cashu.wallet.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A loggable stand-in for a mint quote id.
 *
 * <p>Under NUT-04 anyone who learns the id of a paid, unlocked mint quote can mint it. Logs are
 * shipped and kept, so an id in a log line hands that claim to every reader. A short hash still
 * ties log lines together.
 *
 * <p>The ref is {@code q:} and the first 6 bytes of SHA-256 of the UTF-8 id, in lowercase hex.
 * This is the same ref imani-wallet-lib and imani-gateway-customer log, so one quote can be
 * followed across all of them.
 */
public final class QuoteRef {

    private static final String NO_QUOTE = "(none)";
    private static final String PREFIX = "q:";
    private static final int REF_BYTES = 6;

    /** A ref already in the text, ending where an id character would not follow. */
    private static final String EXISTING_REF = "q:[0-9a-f]{12}(?![0-9a-zA-Z-])";
    private static final String EXISTING_PLACEHOLDER = "\\(none\\)";
    /** Skips a value that is already a ref, so redacting twice does not hash the ref itself. */
    private static final String NOT_A_REF = "(?!" + EXISTING_REF + ")(?!" + EXISTING_PLACEHOLDER + ")";

    /**
     * The shapes a quote id takes in text this client writes or passes on: a {@code quoteId=} or
     * {@code quote_id=} field, a JSON {@code "quote":"..."} member from a request or error body,
     * and the id segment of a quote URL such as {@code /mint/quote/voucher/bolt11/<id>},
     * {@code /mint/quote/bolt11/<id>} or {@code /melt/quote/bolt11/<id>}.
     */
    private static final Pattern QUOTE_URL_SHAPE =
            Pattern.compile("(/quote/(?:voucher/)?(?!voucher/)[a-z0-9_]+/)" + NOT_A_REF + "([^\\s\"'/?#,;)]+)");
    private static final List<Pattern> QUOTE_ID_SHAPES = List.of(
            Pattern.compile("(quote_?[iI]d=)" + NOT_A_REF + "([^,;.\\s)\"'}]+)"),
            Pattern.compile("(\"quote(?:_?[iI]d)?\"\\s*:\\s*\")" + NOT_A_REF + "([^\"]+)"),
            QUOTE_URL_SHAPE);

    private QuoteRef() {
    }

    /** The ref for a quote id, safe to log; {@code (none)} when there is no id. */
    public static String of(String quoteId) {
        if (quoteId == null || quoteId.isEmpty()) {
            return NO_QUOTE;
        }
        return PREFIX + HexFormat.of().formatHex(sha256(quoteId), 0, REF_BYTES);
    }

    /** The text with every quote id it carries, in any of the known shapes, replaced by its ref. */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String redacted = text;
        for (Pattern shape : QUOTE_ID_SHAPES) {
            redacted = replaceValues(shape, redacted);
        }
        return redacted;
    }

    /**
     * As {@link #redact(String)}, and also every literal occurrence of {@code quoteId}, for when the
     * caller knows which quote the text is about and the id may appear in free text, such as a
     * mint's error detail.
     */
    public static String redact(String text, String quoteId) {
        String redacted = redact(text);
        if (redacted == null || quoteId == null || quoteId.isEmpty()) {
            return redacted;
        }
        return redacted.replace(quoteId, of(quoteId));
    }

    /** The quote id in the id segment of a quote URL or path, when there is one. */
    public static Optional<String> idInUrl(String url) {
        Matcher matcher = QUOTE_URL_SHAPE.matcher(url == null ? "" : url);
        return matcher.find() ? Optional.of(matcher.group(2)) : Optional.empty();
    }

    private static String replaceValues(Pattern shape, String text) {
        Matcher matcher = shape.matcher(text);
        StringBuilder redacted = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(redacted, Matcher.quoteReplacement(matcher.group(1) + of(matcher.group(2))));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable. Every Java runtime must provide it.", e);
        }
    }
}
