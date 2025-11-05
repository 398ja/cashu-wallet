package xyz.tcheeric.cashu.wallet.proto.mnemonic;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.bips.bip39.Bip39;
import xyz.tcheeric.cashu.entities.annotation.Nut;

/**
 * Utility class for managing BIP39 mnemonic phrases in the context of NUT-13
 * deterministic wallet recovery.
 *
 * <p>This class provides convenient methods for:
 * <ul>
 *   <li>Generating secure BIP39 mnemonic phrases</li>
 *   <li>Validating mnemonic phrases with detailed error reporting</li>
 *   <li>Deriving BIP32 master keys from mnemonics</li>
 *   <li>Security best practices enforcement</li>
 * </ul>
 *
 * <p><b>Security Considerations:</b>
 * <ul>
 *   <li>Never log mnemonics or display them in production environments</li>
 *   <li>Always validate user-provided mnemonics before use</li>
 *   <li>Clear sensitive data from memory when possible</li>
 *   <li>Use strong passphrases for additional security (optional but recommended)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Generate a new 12-word mnemonic
 * String mnemonic = MnemonicManager.generateMnemonic(12);
 *
 * // Validate a user-provided mnemonic
 * ValidationResult result = MnemonicManager.validateMnemonicWithDetails(userMnemonic);
 * if (!result.isValid()) {
 *     System.err.println("Invalid mnemonic: " + result.getErrorMessage());
 *     return;
 * }
 *
 * // Derive master key for wallet recovery
 * DeterministicKey masterKey = MnemonicManager.loadFromMnemonic(mnemonic, "");
 * }</pre>
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13 Specification</a>
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@Nut(13)
@Slf4j
public final class MnemonicManager {

    /**
     * Recommended default mnemonic word count (12 words = 128 bits entropy).
     */
    public static final int DEFAULT_WORD_COUNT = 12;

    /**
     * Valid BIP39 mnemonic word counts.
     */
    public static final int[] VALID_WORD_COUNTS = {12, 15, 18, 21, 24};

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private MnemonicManager() {
        throw new AssertionError("MnemonicManager is a utility class and should not be instantiated");
    }

    /**
     * Generates a new BIP39 mnemonic phrase with the default word count (12 words).
     *
     * <p>The generated mnemonic provides 128 bits of entropy, which is sufficient
     * for most use cases and recommended by NUT-13.
     *
     * <p><b>Security Warning:</b> The generated mnemonic should be:
     * <ul>
     *   <li>Written down on paper and stored securely</li>
     *   <li>Never stored digitally in plaintext</li>
     *   <li>Never transmitted over insecure channels</li>
     *   <li>Protected from unauthorized access</li>
     * </ul>
     *
     * @return A new 12-word BIP39 mnemonic phrase
     * @throws IllegalStateException if mnemonic generation fails
     *
     * @see #generateMnemonic(int)
     */
    public static String generateMnemonic() {
        return generateMnemonic(DEFAULT_WORD_COUNT);
    }

    /**
     * Generates a new BIP39 mnemonic phrase with the specified word count.
     *
     * <p>Supported word counts and their entropy levels:
     * <ul>
     *   <li>12 words - 128 bits entropy (recommended)</li>
     *   <li>15 words - 160 bits entropy</li>
     *   <li>18 words - 192 bits entropy</li>
     *   <li>21 words - 224 bits entropy</li>
     *   <li>24 words - 256 bits entropy</li>
     * </ul>
     *
     * @param wordCount Number of words in the mnemonic (must be 12, 15, 18, 21, or 24)
     * @return A new BIP39 mnemonic phrase with the specified word count
     * @throws IllegalArgumentException if wordCount is not valid
     * @throws IllegalStateException if mnemonic generation fails
     *
     * @see #generateMnemonic()
     */
    public static String generateMnemonic(int wordCount) {
        validateWordCount(wordCount);

        log.info("mnemonic_generation_started word_count={}", wordCount);

        try {
            String mnemonic = Bip39.generateMnemonic(wordCount);

            log.info("mnemonic_generation_completed word_count={} entropy_bits={}",
                wordCount, calculateEntropyBits(wordCount));

            return mnemonic;

        } catch (Exception e) {
            log.error("mnemonic_generation_failed word_count={} error={}",
                wordCount, e.getMessage());
            throw new IllegalStateException("Failed to generate mnemonic: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a BIP39 mnemonic phrase using simple boolean validation.
     *
     * <p>This method performs complete BIP39 validation including:
     * <ul>
     *   <li>Word count verification</li>
     *   <li>Word list membership check</li>
     *   <li>Checksum validation</li>
     * </ul>
     *
     * <p>For detailed error messages, use {@link #validateMnemonicWithDetails(String)} instead.
     *
     * @param mnemonic The mnemonic phrase to validate
     * @return true if the mnemonic is valid, false otherwise
     * @throws IllegalArgumentException if mnemonic is null
     *
     * @see #validateMnemonicWithDetails(String)
     */
    public static boolean validateMnemonic(@NonNull String mnemonic) {
        log.debug("mnemonic_validation_started");

        boolean isValid = Bip39.isValidMnemonic(mnemonic);

        log.debug("mnemonic_validation_completed valid={}", isValid);

        return isValid;
    }

    /**
     * Validates a BIP39 mnemonic phrase with detailed error reporting.
     *
     * <p>This method provides comprehensive validation results including:
     * <ul>
     *   <li>Validity status (true/false)</li>
     *   <li>Detailed error message if invalid</li>
     *   <li>Specific validation failure reason</li>
     * </ul>
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * ValidationResult result = MnemonicManager.validateMnemonicWithDetails(userInput);
     * if (!result.isValid()) {
     *     System.err.println("Validation failed: " + result.getErrorMessage());
     *     // Show error to user
     * }
     * }</pre>
     *
     * @param mnemonic The mnemonic phrase to validate
     * @return ValidationResult containing validation status and error details
     * @throws IllegalArgumentException if mnemonic is null
     *
     * @see ValidationResult
     * @see #validateMnemonic(String)
     */
    public static ValidationResult validateMnemonicWithDetails(@NonNull String mnemonic) {
        log.debug("mnemonic_detailed_validation_started");

        try {
            var result = Bip39.validateMnemonic(mnemonic);

            log.debug("mnemonic_detailed_validation_completed valid={} error={}",
                result.isValid(), result.getErrorMessage());

            return new ValidationResult(result.isValid(), result.getErrorMessage());

        } catch (Exception e) {
            log.error("mnemonic_detailed_validation_error error={}", e.getMessage());
            return new ValidationResult(false, "Validation error: " + e.getMessage());
        }
    }

    /**
     * Derives a BIP32 master key from a BIP39 mnemonic phrase.
     *
     * <p>The master key is used as the root for all hierarchical deterministic
     * key derivation in NUT-13 wallet recovery.
     *
     * <p><b>Important:</b>
     * <ul>
     *   <li>The mnemonic MUST be validated before calling this method</li>
     *   <li>Invalid mnemonics will cause exceptions</li>
     *   <li>The passphrase is optional but recommended for additional security</li>
     *   <li>An empty string "" is used when no passphrase is desired</li>
     * </ul>
     *
     * @param mnemonic The BIP39 mnemonic phrase (must be valid)
     * @param passphrase Optional passphrase for additional security (use "" for none)
     * @return The derived BIP32 master key
     * @throws IllegalArgumentException if mnemonic is null, empty, or invalid
     * @throws IllegalStateException if key derivation fails
     *
     * @see #loadFromMnemonicUnsafe(String, String)
     */
    public static DeterministicKey loadFromMnemonic(
            @NonNull String mnemonic,
            @NonNull String passphrase
    ) {
        log.debug("master_key_derivation_started");

        // Validate mnemonic first
        if (!validateMnemonic(mnemonic)) {
            ValidationResult result = validateMnemonicWithDetails(mnemonic);
            log.error("master_key_derivation_failed_invalid_mnemonic error={}",
                result.getErrorMessage());
            throw new IllegalArgumentException(
                "Invalid mnemonic phrase: " + result.getErrorMessage()
            );
        }

        try {
            DeterministicKey masterKey = Bip39.mnemonicToMasterKey(mnemonic, passphrase);

            log.info("master_key_derivation_completed has_passphrase={}",
                !passphrase.isEmpty());

            return masterKey;

        } catch (Exception e) {
            log.error("master_key_derivation_failed error={}", e.getMessage());
            throw new IllegalStateException("Failed to derive master key: " + e.getMessage(), e);
        }
    }

    /**
     * Derives a BIP32 master key from a mnemonic WITHOUT validation.
     *
     * <p><b>WARNING:</b> This method skips mnemonic validation for performance reasons.
     * Use only when you are certain the mnemonic is valid (e.g., it was just generated
     * or has already been validated).
     *
     * <p>For most use cases, prefer {@link #loadFromMnemonic(String, String)} which
     * includes validation.
     *
     * @param mnemonic The BIP39 mnemonic phrase (assumed valid)
     * @param passphrase Optional passphrase for additional security (use "" for none)
     * @return The derived BIP32 master key
     * @throws IllegalArgumentException if mnemonic or passphrase is null
     * @throws IllegalStateException if key derivation fails
     *
     * @see #loadFromMnemonic(String, String)
     */
    public static DeterministicKey loadFromMnemonicUnsafe(
            @NonNull String mnemonic,
            @NonNull String passphrase
    ) {
        log.debug("master_key_derivation_unsafe_started");

        try {
            DeterministicKey masterKey = Bip39.mnemonicToMasterKey(mnemonic, passphrase);

            log.info("master_key_derivation_unsafe_completed");

            return masterKey;

        } catch (Exception e) {
            log.error("master_key_derivation_unsafe_failed error={}", e.getMessage());
            throw new IllegalStateException("Failed to derive master key: " + e.getMessage(), e);
        }
    }

    /**
     * Normalizes a mnemonic phrase by trimming whitespace, converting to lowercase,
     * and collapsing multiple spaces.
     *
     * <p>This is useful for handling user input that may have inconsistent formatting.
     *
     * @param mnemonic The mnemonic phrase to normalize
     * @return The normalized mnemonic phrase
     * @throws IllegalArgumentException if mnemonic is null
     */
    public static String normalizeMnemonic(@NonNull String mnemonic) {
        // Trim, lowercase, collapse multiple spaces to single space
        return mnemonic.trim()
            .toLowerCase()
            .replaceAll("\\s+", " ");
    }

    /**
     * Calculates the number of words in a mnemonic phrase.
     *
     * @param mnemonic The mnemonic phrase
     * @return The word count
     * @throws IllegalArgumentException if mnemonic is null or empty
     */
    public static int getWordCount(@NonNull String mnemonic) {
        String normalized = normalizeMnemonic(mnemonic);
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split(" ").length;
    }

    /**
     * Checks if a word count is valid for BIP39 mnemonics.
     *
     * @param wordCount The word count to check
     * @return true if the word count is valid (12, 15, 18, 21, or 24)
     */
    public static boolean isValidWordCount(int wordCount) {
        for (int validCount : VALID_WORD_COUNTS) {
            if (wordCount == validCount) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the entropy bits for a given word count.
     *
     * @param wordCount The number of words
     * @return The entropy in bits
     * @throws IllegalArgumentException if word count is invalid
     */
    public static int calculateEntropyBits(int wordCount) {
        validateWordCount(wordCount);
        // ENT = (wordCount * 11) - checksum bits
        // checksum bits = ENT / 32
        // Therefore: wordCount * 11 = ENT + ENT/32 = ENT * 33/32
        // So: ENT = (wordCount * 11 * 32) / 33
        return (wordCount * 11 * 32) / 33;
    }

    // Private helper methods

    private static void validateWordCount(int wordCount) {
        if (!isValidWordCount(wordCount)) {
            throw new IllegalArgumentException(
                String.format(
                    "Invalid word count: %d. Must be one of: 12, 15, 18, 21, or 24",
                    wordCount
                )
            );
        }
    }

    /**
     * Result of mnemonic validation with detailed error information.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        /**
         * @return true if the mnemonic is valid
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * @return Detailed error message if validation failed, or empty string if valid
         */
        public String getErrorMessage() {
            return errorMessage != null ? errorMessage : "";
        }

        @Override
        public String toString() {
            return valid
                ? "ValidationResult{valid=true}"
                : String.format("ValidationResult{valid=false, error='%s'}", errorMessage);
        }
    }
}
