package xyz.tcheeric.cashu.wallet.proto.mnemonic;

import org.bitcoinj.crypto.DeterministicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link MnemonicManager}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Mnemonic generation with various word counts</li>
 *   <li>Mnemonic validation (simple and detailed)</li>
 *   <li>Master key derivation with and without passphrases</li>
 *   <li>Utility methods (normalization, word counting, etc.)</li>
 *   <li>Error handling and edge cases</li>
 * </ul>
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@DisplayName("MnemonicManager Tests")
class MnemonicManagerTest {

    // ========================================
    // Mnemonic Generation Tests
    // ========================================

    @Test
    @DisplayName("Should generate 12-word mnemonic by default")
    void testGenerateDefaultMnemonic() {
        String mnemonic = MnemonicManager.generateMnemonic();

        assertNotNull(mnemonic, "Generated mnemonic should not be null");
        assertFalse(mnemonic.isEmpty(), "Generated mnemonic should not be empty");

        int wordCount = MnemonicManager.getWordCount(mnemonic);
        assertEquals(12, wordCount, "Default mnemonic should have 12 words");

        assertTrue(MnemonicManager.validateMnemonic(mnemonic),
            "Generated mnemonic should be valid");
    }

    @Test
    @DisplayName("Should generate valid mnemonics for all supported word counts")
    void testGenerateMnemonicWithVariousWordCounts() {
        for (int wordCount : new int[]{12, 15, 18, 21, 24}) {
            String mnemonic = MnemonicManager.generateMnemonic(wordCount);

            assertNotNull(mnemonic);
            assertEquals(wordCount, MnemonicManager.getWordCount(mnemonic),
                "Generated mnemonic should have " + wordCount + " words");

            assertTrue(MnemonicManager.validateMnemonic(mnemonic),
                "Generated mnemonic should be valid");
        }
    }

    @Test
    @DisplayName("Should throw exception for invalid word count")
    void testGenerateMnemonicWithInvalidWordCount() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MnemonicManager.generateMnemonic(10)
        );

        assertTrue(exception.getMessage().contains("Invalid word count"),
            "Exception message should mention invalid word count");
    }

    @Test
    @DisplayName("Should reject invalid word counts")
    void testRejectInvalidWordCounts() {
        for (int invalidWordCount : new int[]{11, 13, 16, 20, 25, 0, -1}) {
            assertThrows(IllegalArgumentException.class,
                () -> MnemonicManager.generateMnemonic(invalidWordCount));
        }
    }

    @Test
    @DisplayName("Generated mnemonics should be unique")
    void testGeneratedMnemonicsAreUnique() {
        String mnemonic1 = MnemonicManager.generateMnemonic();
        String mnemonic2 = MnemonicManager.generateMnemonic();

        assertNotEquals(mnemonic1, mnemonic2,
            "Consecutive mnemonic generations should produce different results");
    }

    // ========================================
    // Mnemonic Validation Tests
    // ========================================

    @Test
    @DisplayName("Should validate correct 12-word mnemonic")
    void testValidateCorrectMnemonic() {
        // Known valid BIP39 mnemonic
        String validMnemonic = "abandon abandon abandon abandon abandon abandon " +
                               "abandon abandon abandon abandon abandon about";

        assertTrue(MnemonicManager.validateMnemonic(validMnemonic),
            "Known valid mnemonic should pass validation");
    }

    @Test
    @DisplayName("Should reject invalid mnemonic with wrong word")
    void testValidateInvalidMnemonicWithWrongWord() {
        String invalidMnemonic = "invalid abandon abandon abandon abandon abandon " +
                                "abandon abandon abandon abandon abandon about";

        assertFalse(MnemonicManager.validateMnemonic(invalidMnemonic),
            "Mnemonic with invalid word should fail validation");
    }

    @Test
    @DisplayName("Should reject mnemonic with wrong word count")
    void testValidateMnemonicWithWrongWordCount() {
        String invalidMnemonic = "abandon abandon abandon abandon abandon";

        assertFalse(MnemonicManager.validateMnemonic(invalidMnemonic),
            "Mnemonic with wrong word count should fail validation");
    }

    @Test
    @DisplayName("Should reject empty mnemonic")
    void testValidateEmptyMnemonic() {
        assertFalse(MnemonicManager.validateMnemonic(""),
            "Empty mnemonic should fail validation");
    }

    @Test
    @DisplayName("Should reject mnemonic with invalid checksum")
    void testValidateMnemonicWithInvalidChecksum() {
        // 12 valid words but invalid checksum
        String invalidMnemonic = "abandon abandon abandon abandon abandon abandon " +
                                "abandon abandon abandon abandon abandon abandon";

        assertFalse(MnemonicManager.validateMnemonic(invalidMnemonic),
            "Mnemonic with invalid checksum should fail validation");
    }

    @Test
    @DisplayName("Should throw exception for null mnemonic in validation")
    void testValidateNullMnemonic() {
        assertThrows(NullPointerException.class,
            () -> MnemonicManager.validateMnemonic(null));
    }

    // ========================================
    // Detailed Validation Tests
    // ========================================

    @Test
    @DisplayName("Should provide detailed validation result for valid mnemonic")
    void testDetailedValidationForValidMnemonic() {
        String validMnemonic = "abandon abandon abandon abandon abandon abandon " +
                              "abandon abandon abandon abandon abandon about";

        MnemonicManager.ValidationResult result =
            MnemonicManager.validateMnemonicWithDetails(validMnemonic);

        assertTrue(result.isValid(), "Validation result should be valid");
        assertTrue(result.getErrorMessage().isEmpty(),
            "Error message should be empty for valid mnemonic");
    }

    @Test
    @DisplayName("Should provide detailed error for invalid mnemonic")
    void testDetailedValidationForInvalidMnemonic() {
        String invalidMnemonic = "invalid word sequence that makes no sense";

        MnemonicManager.ValidationResult result =
            MnemonicManager.validateMnemonicWithDetails(invalidMnemonic);

        assertFalse(result.isValid(), "Validation result should be invalid");
        assertFalse(result.getErrorMessage().isEmpty(),
            "Error message should not be empty for invalid mnemonic");
    }

    @Test
    @DisplayName("Should provide detailed error for empty mnemonic")
    void testDetailedValidationForEmptyMnemonic() {
        MnemonicManager.ValidationResult result =
            MnemonicManager.validateMnemonicWithDetails("");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("empty") ||
                   result.getErrorMessage().contains("word count"),
            "Error message should mention empty or word count issue");
    }

    @Test
    @DisplayName("ValidationResult toString should be informative")
    void testValidationResultToString() {
        MnemonicManager.ValidationResult validResult =
            new MnemonicManager.ValidationResult(true, null);
        assertTrue(validResult.toString().contains("valid=true"));

        MnemonicManager.ValidationResult invalidResult =
            new MnemonicManager.ValidationResult(false, "Test error");
        assertTrue(invalidResult.toString().contains("valid=false"));
        assertTrue(invalidResult.toString().contains("Test error"));
    }

    // ========================================
    // Master Key Derivation Tests
    // ========================================

    @Test
    @DisplayName("Should derive master key from valid mnemonic without passphrase")
    void testLoadFromMnemonicWithoutPassphrase() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";

        DeterministicKey masterKey = MnemonicManager.loadFromMnemonic(mnemonic, "");

        assertNotNull(masterKey, "Master key should not be null");
        assertTrue(masterKey.isPubKeyOnly() == false, "Master key should have private key");
    }

    @Test
    @DisplayName("Should derive master key from valid mnemonic with passphrase")
    void testLoadFromMnemonicWithPassphrase() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";
        String passphrase = "test passphrase";

        DeterministicKey masterKey = MnemonicManager.loadFromMnemonic(mnemonic, passphrase);

        assertNotNull(masterKey, "Master key should not be null");
        assertTrue(masterKey.isPubKeyOnly() == false, "Master key should have private key");
    }

    @Test
    @DisplayName("Different passphrases should produce different master keys")
    void testDifferentPassphrasesProduceDifferentKeys() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";

        DeterministicKey key1 = MnemonicManager.loadFromMnemonic(mnemonic, "");
        DeterministicKey key2 = MnemonicManager.loadFromMnemonic(mnemonic, "passphrase");

        assertNotEquals(key1.getPrivateKeyAsHex(), key2.getPrivateKeyAsHex(),
            "Different passphrases should produce different keys");
    }

    @Test
    @DisplayName("Same mnemonic and passphrase should produce same master key")
    void testDeterministicKeyDerivation() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";
        String passphrase = "test";

        DeterministicKey key1 = MnemonicManager.loadFromMnemonic(mnemonic, passphrase);
        DeterministicKey key2 = MnemonicManager.loadFromMnemonic(mnemonic, passphrase);

        assertEquals(key1.getPrivateKeyAsHex(), key2.getPrivateKeyAsHex(),
            "Same mnemonic and passphrase should produce same key");
    }

    @Test
    @DisplayName("Should reject invalid mnemonic in loadFromMnemonic")
    void testLoadFromInvalidMnemonic() {
        String invalidMnemonic = "invalid mnemonic phrase";

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MnemonicManager.loadFromMnemonic(invalidMnemonic, "")
        );

        assertTrue(exception.getMessage().contains("Invalid mnemonic"),
            "Exception should mention invalid mnemonic");
    }

    @Test
    @DisplayName("Should throw exception for null mnemonic in loadFromMnemonic")
    void testLoadFromNullMnemonic() {
        assertThrows(NullPointerException.class,
            () -> MnemonicManager.loadFromMnemonic(null, ""));
    }

    @Test
    @DisplayName("Should throw exception for null passphrase in loadFromMnemonic")
    void testLoadFromMnemonicWithNullPassphrase() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";

        assertThrows(NullPointerException.class,
            () -> MnemonicManager.loadFromMnemonic(mnemonic, null));
    }

    // ========================================
    // Unsafe Master Key Derivation Tests
    // ========================================

    @Test
    @DisplayName("Should derive master key using unsafe method")
    void testLoadFromMnemonicUnsafe() {
        String mnemonic = MnemonicManager.generateMnemonic();

        DeterministicKey masterKey = MnemonicManager.loadFromMnemonicUnsafe(mnemonic, "");

        assertNotNull(masterKey);
        assertFalse(masterKey.isPubKeyOnly());
    }

    @Test
    @DisplayName("Unsafe method should produce same key as safe method")
    void testUnsafeMethodProducesSameKey() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                         "abandon abandon abandon abandon abandon about";

        DeterministicKey safeDerived = MnemonicManager.loadFromMnemonic(mnemonic, "");
        DeterministicKey unsafeDerived = MnemonicManager.loadFromMnemonicUnsafe(mnemonic, "");

        assertEquals(safeDerived.getPrivateKeyAsHex(), unsafeDerived.getPrivateKeyAsHex(),
            "Safe and unsafe methods should produce the same key");
    }

    // Note: Unsafe method with invalid mnemonic may or may not throw depending on
    // the underlying implementation. We don't test this as it's unpredictable behavior.

    // ========================================
    // Normalization Tests
    // ========================================

    @Test
    @DisplayName("Should normalize mnemonic with extra whitespace")
    void testNormalizeMnemonicWithExtraWhitespace() {
        String messy = "  abandon  abandon   abandon  ";
        String normalized = MnemonicManager.normalizeMnemonic(messy);

        assertEquals("abandon abandon abandon", normalized);
    }

    @Test
    @DisplayName("Should normalize mnemonic to lowercase")
    void testNormalizeMnemonicToLowercase() {
        String uppercase = "ABANDON ABANDON ABANDON";
        String normalized = MnemonicManager.normalizeMnemonic(uppercase);

        assertEquals("abandon abandon abandon", normalized);
    }

    @Test
    @DisplayName("Should normalize mixed case and whitespace")
    void testNormalizeMixedCaseAndWhitespace() {
        String messy = "  Abandon  ABANDON   aBaNdOn  ";
        String normalized = MnemonicManager.normalizeMnemonic(messy);

        assertEquals("abandon abandon abandon", normalized);
    }

    @Test
    @DisplayName("Should handle tabs and newlines in normalization")
    void testNormalizeWithTabsAndNewlines() {
        String messy = "abandon\tabandon\nabandon\r\nabandon";
        String normalized = MnemonicManager.normalizeMnemonic(messy);

        assertEquals("abandon abandon abandon abandon", normalized);
    }

    // ========================================
    // Word Count Tests
    // ========================================

    @Test
    @DisplayName("Should count words correctly")
    void testGetWordCount() {
        assertEquals(12, MnemonicManager.getWordCount(
            "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"));
        assertEquals(1, MnemonicManager.getWordCount("word"));
        assertEquals(0, MnemonicManager.getWordCount(""));
        assertEquals(0, MnemonicManager.getWordCount("   "));
    }

    @Test
    @DisplayName("Should count words after normalization")
    void testGetWordCountWithMessyInput() {
        assertEquals(3, MnemonicManager.getWordCount("  word1   word2  word3  "));
    }

    @Test
    @DisplayName("Should throw exception for null in getWordCount")
    void testGetWordCountWithNull() {
        assertThrows(NullPointerException.class,
            () -> MnemonicManager.getWordCount(null));
    }

    // ========================================
    // Word Count Validation Tests
    // ========================================

    @Test
    @DisplayName("Should recognize valid word counts")
    void testIsValidWordCount() {
        for (int wordCount : new int[]{12, 15, 18, 21, 24}) {
            assertTrue(MnemonicManager.isValidWordCount(wordCount));
        }
    }

    @Test
    @DisplayName("Should reject invalid word counts")
    void testIsInvalidWordCount() {
        for (int wordCount : new int[]{0, 1, 11, 13, 14, 16, 20, 25, 100, -1}) {
            assertFalse(MnemonicManager.isValidWordCount(wordCount));
        }
    }

    // ========================================
    // Entropy Calculation Tests
    // ========================================

    @Test
    @DisplayName("Should calculate correct entropy for 12 words")
    void testCalculateEntropyFor12Words() {
        assertEquals(128, MnemonicManager.calculateEntropyBits(12));
    }

    @Test
    @DisplayName("Should calculate correct entropy for 24 words")
    void testCalculateEntropyFor24Words() {
        assertEquals(256, MnemonicManager.calculateEntropyBits(24));
    }

    @Test
    @DisplayName("Should calculate entropy for all valid word counts")
    void testCalculateEntropyForAllValidWordCounts() {
        for (int wordCount : new int[]{12, 15, 18, 21, 24}) {
            int entropy = MnemonicManager.calculateEntropyBits(wordCount);
            assertTrue(entropy > 0, "Entropy should be positive");
            assertTrue(entropy % 32 == 0, "Entropy should be a multiple of 32");
        }
    }

    @Test
    @DisplayName("Should throw exception for invalid word count in entropy calculation")
    void testCalculateEntropyForInvalidWordCount() {
        assertThrows(IllegalArgumentException.class,
            () -> MnemonicManager.calculateEntropyBits(10));
    }

    // ========================================
    // Constants Tests
    // ========================================

    @Test
    @DisplayName("Should have correct default word count")
    void testDefaultWordCount() {
        assertEquals(12, MnemonicManager.DEFAULT_WORD_COUNT);
    }

    @Test
    @DisplayName("Should have correct valid word counts list")
    void testValidWordCountsList() {
        assertEquals(java.util.List.of(12, 15, 18, 21, 24), MnemonicManager.getValidWordCounts());
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Test
    @DisplayName("Should not allow instantiation of utility class")
    void testCannotInstantiateMnemonicManager() {
        // MnemonicManager is a utility class with private constructor
        // This test ensures it cannot be instantiated via reflection
        assertThrows(Exception.class, () -> {
            var constructor = MnemonicManager.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }

    // ========================================
    // Integration Tests
    // ========================================

    @Test
    @DisplayName("Complete workflow: generate, validate, derive key")
    void testCompleteWorkflow() {
        // Step 1: Generate mnemonic
        String mnemonic = MnemonicManager.generateMnemonic(12);
        assertNotNull(mnemonic);

        // Step 2: Validate it
        assertTrue(MnemonicManager.validateMnemonic(mnemonic));

        // Step 3: Get detailed validation
        MnemonicManager.ValidationResult result =
            MnemonicManager.validateMnemonicWithDetails(mnemonic);
        assertTrue(result.isValid());

        // Step 4: Derive master key
        DeterministicKey masterKey = MnemonicManager.loadFromMnemonic(mnemonic, "");
        assertNotNull(masterKey);

        // Step 5: Verify deterministic behavior
        DeterministicKey masterKey2 = MnemonicManager.loadFromMnemonic(mnemonic, "");
        assertEquals(masterKey.getPrivateKeyAsHex(), masterKey2.getPrivateKeyAsHex());
    }

    @Test
    @DisplayName("User input normalization workflow")
    void testUserInputNormalizationWorkflow() {
        // Simulate messy user input
        String userInput = "  ABANDON  abandon   Abandon ABANDON abandon  abandon  " +
                          "abandon  ABANDON abandon   abandon  ABANDON about  ";

        // Step 1: Normalize
        String normalized = MnemonicManager.normalizeMnemonic(userInput);

        // Step 2: Validate normalized input
        assertTrue(MnemonicManager.validateMnemonic(normalized));

        // Step 3: Derive key
        DeterministicKey key = MnemonicManager.loadFromMnemonic(normalized, "");
        assertNotNull(key);
    }
}
