package xyz.tcheeric.cashu.wallet.proto.mnemonic;

/**
 * Comprehensive security recommendations for BIP39 mnemonic phrase storage and handling
 * in the context of NUT-13 deterministic wallet recovery.
 *
 * <p>This class serves as a central reference for security best practices. It does not
 * contain executable code but provides detailed guidance through documentation.
 *
 * <h2>Overview</h2>
 * <p>A BIP39 mnemonic phrase is the master key to your Cashu wallet. Anyone with access to
 * your mnemonic can derive all your secrets and access all your tokens. Proper storage and
 * handling is critical to wallet security.
 *
 * <h2>Threat Model</h2>
 * <p>When securing a mnemonic, consider these threat categories:
 * <ul>
 *   <li><b>Physical theft:</b> Unauthorized physical access to storage location</li>
 *   <li><b>Digital compromise:</b> Malware, keyloggers, or unauthorized system access</li>
 *   <li><b>Social engineering:</b> Phishing, impersonation, or coercion</li>
 *   <li><b>Natural disasters:</b> Fire, flood, earthquake, or other catastrophic events</li>
 *   <li><b>Loss/destruction:</b> Accidental deletion, hardware failure, or memory loss</li>
 *   <li><b>Supply chain attacks:</b> Compromised wallet software or hardware</li>
 * </ul>
 *
 * <h2>Physical Storage Best Practices</h2>
 *
 * <h3>Recommended: Paper Backup</h3>
 * <p><b>Method:</b> Write mnemonic on paper and store in secure location.
 * <ul>
 *   <li>✓ Use acid-free archival paper</li>
 *   <li>✓ Write with archival-quality pen or pencil</li>
 *   <li>✓ Write clearly and legibly to avoid transcription errors</li>
 *   <li>✓ Number each word (1-12, 1-24) to prevent ordering mistakes</li>
 *   <li>✓ Store in fireproof/waterproof safe or safety deposit box</li>
 *   <li>✓ Consider laminating or using weatherproof paper</li>
 *   <li>✗ Never photocopy or photograph the mnemonic</li>
 *   <li>✗ Don't store near computers, phones, or IoT devices</li>
 * </ul>
 *
 * <h3>Advanced: Metal Backup</h3>
 * <p><b>Method:</b> Engrave or stamp mnemonic onto metal plate.
 * <ul>
 *   <li>✓ Use stainless steel or titanium for durability</li>
 *   <li>✓ Resist fire (up to 1,400°C), water, and corrosion</li>
 *   <li>✓ Consider commercial products (Cryptosteel, Billfodl, etc.)</li>
 *   <li>✓ Store in multiple secure locations for redundancy</li>
 *   <li>⚠ More expensive but provides maximum physical durability</li>
 * </ul>
 *
 * <h3>Geographic Distribution</h3>
 * <p><b>Strategy:</b> Store backups in multiple physical locations.
 * <ul>
 *   <li>Primary: Home safe (fireproof/waterproof)</li>
 *   <li>Secondary: Bank safety deposit box</li>
 *   <li>Tertiary: Trusted family member or attorney (sealed envelope)</li>
 *   <li>⚠ Ensure locations are geographically separated (different cities/regions)</li>
 *   <li>⚠ Balance accessibility vs. security based on your threat model</li>
 * </ul>
 *
 * <h2>Digital Storage Security</h2>
 *
 * <h3>Strongly Discouraged: Plaintext Digital Storage</h3>
 * <p><b>Never store mnemonics in:</b>
 * <ul>
 *   <li>✗ Text files on computers or phones</li>
 *   <li>✗ Email (sent or saved)</li>
 *   <li>✗ Cloud storage (Dropbox, Google Drive, iCloud)</li>
 *   <li>✗ Password managers (unless specifically designed for mnemonics)</li>
 *   <li>✗ Screenshots or photos</li>
 *   <li>✗ Note-taking apps (Evernote, OneNote, etc.)</li>
 *   <li>✗ Messaging apps (even "encrypted" ones)</li>
 *   <li>✗ Browser bookmarks or autofill</li>
 * </ul>
 * <p><b>Rationale:</b> Digital devices are vulnerable to malware, backups may be
 * unencrypted, and cloud services can be compromised or accessed by third parties.
 *
 * <h3>Acceptable: Encrypted Digital Storage</h3>
 * <p>If digital storage is necessary, follow these requirements:
 * <ul>
 *   <li><b>Encryption:</b> Use AES-256 or stronger</li>
 *   <li><b>Key Derivation:</b> Strong passphrase with KDF (Argon2id, PBKDF2 ≥100k iterations)</li>
 *   <li><b>Storage Location:</b> Offline device or air-gapped computer</li>
 *   <li><b>Implementation:</b> Use {@link MnemonicManager} with secure passphrase</li>
 *   <li>⚠ The passphrase becomes a single point of failure</li>
 *   <li>⚠ Store passphrase hint separately (NOT with encrypted mnemonic)</li>
 * </ul>
 *
 * <h3>Example: Secure Digital Backup</h3>
 * <pre>{@code
 * // Generate mnemonic
 * String mnemonic = MnemonicManager.generateMnemonic(12);
 *
 * // User provides strong passphrase
 * String passphrase = userInput.getPassphrase(); // e.g., "correct horse battery staple 1234!@#$"
 *
 * // Encrypt using AES-256 (implementation detail)
 * String encrypted = AesEncryption.encrypt(mnemonic, passphrase);
 *
 * // Store encrypted data on offline USB drive
 * Files.writeString(offlineUsbPath.resolve("wallet_backup.enc"), encrypted);
 *
 * // Store passphrase hint separately (NOT on same device)
 * String hint = "childhood_pet + favorite_song + graduation_year + symbols";
 * }</pre>
 *
 * <h2>Passphrase Recommendations</h2>
 *
 * <h3>BIP39 Passphrase (25th Word)</h3>
 * <p>BIP39 supports an optional passphrase that acts as a "25th word":
 * <ul>
 *   <li><b>Purpose:</b> Additional security layer beyond 12/24 word mnemonic</li>
 *   <li><b>Effect:</b> Different passphrases produce different wallets</li>
 *   <li><b>Storage:</b> Must be remembered or stored separately from mnemonic</li>
 *   <li><b>Loss:</b> Losing passphrase means losing access to wallet</li>
 *   <li><b>Recommendation:</b> Use for high-value wallets, store hint securely</li>
 * </ul>
 *
 * <h3>Strong Passphrase Guidelines</h3>
 * <p>When using BIP39 passphrases or encrypting mnemonics:
 * <ul>
 *   <li>✓ Minimum 20 characters</li>
 *   <li>✓ Mix of uppercase, lowercase, numbers, symbols</li>
 *   <li>✓ Use diceware or random generation</li>
 *   <li>✓ Avoid dictionary words, personal information, dates</li>
 *   <li>✓ Consider memorable phrases: "correct horse battery staple" + modifiers</li>
 *   <li>✓ Use passphrase manager for complexity</li>
 *   <li>⚠ Balance complexity with recoverability</li>
 * </ul>
 *
 * <h3>Passphrase Storage Strategy</h3>
 * <p><b>Two-Factor Storage:</b> Split secrets across multiple locations
 * <ul>
 *   <li>Location A: Mnemonic words (without passphrase)</li>
 *   <li>Location B: Passphrase (without mnemonic)</li>
 *   <li>Benefit: Compromising one location doesn't grant wallet access</li>
 *   <li>Risk: Must access both locations to recover wallet</li>
 * </ul>
 *
 * <h2>Backup and Recovery Procedures</h2>
 *
 * <h3>Initial Backup Process</h3>
 * <ol>
 *   <li><b>Generate:</b> Use {@link MnemonicManager#generateMnemonic()}</li>
 *   <li><b>Verify:</b> Write down all words correctly (check spelling)</li>
 *   <li><b>Test:</b> Perform test recovery to ensure words are correct</li>
 *   <li><b>Store:</b> Place in primary secure location</li>
 *   <li><b>Backup:</b> Create secondary copies for geographic distribution</li>
 *   <li><b>Destroy:</b> Securely destroy any temporary notes/printouts</li>
 *   <li><b>Document:</b> Record storage locations (NOT mnemonic) in estate plan</li>
 * </ol>
 *
 * <h3>Test Recovery</h3>
 * <pre>{@code
 * // Before storing backup, test recovery
 * String originalMnemonic = MnemonicManager.generateMnemonic();
 *
 * // Simulate recovery: user reads backup and enters mnemonic
 * String recoveredMnemonic = userInput.getMnemonic();
 *
 * // Validate recovered mnemonic
 * if (!MnemonicManager.validateMnemonic(recoveredMnemonic)) {
 *     throw new RuntimeException("Backup transcription error detected!");
 * }
 *
 * // Verify same master key
 * DeterministicKey original = MnemonicManager.loadFromMnemonic(originalMnemonic, "");
 * DeterministicKey recovered = MnemonicManager.loadFromMnemonic(recoveredMnemonic, "");
 * if (!original.getPrivateKeyAsHex().equals(recovered.getPrivateKeyAsHex())) {
 *     throw new RuntimeException("Recovery produced different key!");
 * }
 * }</pre>
 *
 * <h3>Periodic Verification</h3>
 * <ul>
 *   <li>Verify backups annually or after major life events</li>
 *   <li>Check physical condition (fading ink, water damage, etc.)</li>
 *   <li>Test recovery process to ensure you remember procedure</li>
 *   <li>Update estate planning documents if storage locations change</li>
 * </ul>
 *
 * <h3>Inheritance Planning</h3>
 * <p>Ensure your heirs can access wallets:
 * <ul>
 *   <li>Document wallet existence and recovery procedure (not mnemonic)</li>
 *   <li>Provide storage location hints in will or trust</li>
 *   <li>Consider splitting mnemonic using Shamir's Secret Sharing</li>
 *   <li>Educate trusted person on recovery process</li>
 *   <li>⚠ Balance inheritance vs. security during lifetime</li>
 * </ul>
 *
 * <h2>Operational Security</h2>
 *
 * <h3>Generation Security</h3>
 * <p>When generating a new mnemonic:
 * <ul>
 *   <li>✓ Use trusted, open-source wallet software</li>
 *   <li>✓ Generate on offline/air-gapped device when possible</li>
 *   <li>✓ Verify software integrity (signatures, checksums)</li>
 *   <li>✓ Use adequate entropy source (hardware RNG preferred)</li>
 *   <li>✗ Never use online mnemonic generators</li>
 *   <li>✗ Don't trust pre-generated mnemonics</li>
 *   <li>✗ Don't generate on compromised or untrusted devices</li>
 * </ul>
 *
 * <h3>Entry Security</h3>
 * <p>When entering mnemonic for recovery:
 * <ul>
 *   <li>✓ Use trusted device in private location</li>
 *   <li>✓ Disable cameras and microphones</li>
 *   <li>✓ Check for shoulder surfing or surveillance</li>
 *   <li>✓ Clear clipboard after entry</li>
 *   <li>✗ Don't enter on public/shared computers</li>
 *   <li>✗ Avoid typing in applications that sync or backup</li>
 * </ul>
 *
 * <h3>Transmission Security</h3>
 * <p><b>Never transmit mnemonics:</b>
 * <ul>
 *   <li>✗ Over email, even if "encrypted"</li>
 *   <li>✗ Through messaging apps</li>
 *   <li>✗ Via phone or video call</li>
 *   <li>✗ Through remote desktop/screen sharing</li>
 *   <li>✗ To support personnel or "wallet recovery services"</li>
 * </ul>
 *
 * <h2>Attack Vectors and Mitigations</h2>
 *
 * <h3>Phishing Attacks</h3>
 * <p><b>Attack:</b> Fake wallet apps or websites requesting mnemonic
 * <p><b>Mitigation:</b>
 * <ul>
 *   <li>Legitimate wallets NEVER ask for existing mnemonics</li>
 *   <li>Verify app authenticity (official repositories, signatures)</li>
 *   <li>Bookmark official wallet sites</li>
 *   <li>Be suspicious of unsolicited "recovery" requests</li>
 * </ul>
 *
 * <h3>Clipboard Malware</h3>
 * <p><b>Attack:</b> Malware monitors/modifies clipboard contents
 * <p><b>Mitigation:</b>
 * <ul>
 *   <li>Never copy/paste mnemonics</li>
 *   <li>Type manually or use hardware wallets</li>
 *   <li>Clear clipboard after use</li>
 *   <li>Use clipboard managers with encryption</li>
 * </ul>
 *
 * <h3>Screen Capture Malware</h3>
 * <p><b>Attack:</b> Malware captures screenshots or screen recordings
 * <p><b>Mitigation:</b>
 * <ul>
 *   <li>Use offline device for mnemonic entry</li>
 *   <li>Disable cloud backup of screenshots</li>
 *   <li>Cover screen when entering mnemonic</li>
 *   <li>Run anti-malware scans regularly</li>
 * </ul>
 *
 * <h3>Supply Chain Attacks</h3>
 * <p><b>Attack:</b> Compromised wallet software or hardware
 * <p><b>Mitigation:</b>
 * <ul>
 *   <li>Use open-source, audited wallet software</li>
 *   <li>Verify signatures and checksums</li>
 *   <li>Download from official sources only</li>
 *   <li>Consider hardware wallet for high-value storage</li>
 *   <li>Test with small amounts before large transfers</li>
 * </ul>
 *
 * <h2>Security Checklist</h2>
 *
 * <h3>Mnemonic Generation ✓</h3>
 * <ul>
 *   <li>☐ Generated on trusted, offline device</li>
 *   <li>☐ Used adequate entropy source</li>
 *   <li>☐ Verified software integrity</li>
 *   <li>☐ Never used online generator</li>
 * </ul>
 *
 * <h3>Physical Backup ✓</h3>
 * <ul>
 *   <li>☐ Written on archival paper with quality pen</li>
 *   <li>☐ Words numbered to prevent ordering errors</li>
 *   <li>☐ Stored in fireproof/waterproof container</li>
 *   <li>☐ Multiple backups in geographically separated locations</li>
 *   <li>☐ Tested recovery from backup</li>
 * </ul>
 *
 * <h3>Digital Security ✓</h3>
 * <ul>
 *   <li>☐ No plaintext digital copies exist</li>
 *   <li>☐ Not stored in cloud services</li>
 *   <li>☐ Not in email or messaging apps</li>
 *   <li>☐ Not in screenshots or photos</li>
 *   <li>☐ Encrypted copies use strong passphrase (if any)</li>
 * </ul>
 *
 * <h3>Operational Security ✓</h3>
 * <ul>
 *   <li>☐ Never transmitted electronically</li>
 *   <li>☐ Never shared with support personnel</li>
 *   <li>☐ Entered only on trusted devices</li>
 *   <li>☐ Protected from physical observation</li>
 *   <li>☐ Inheritance plan documented (without revealing mnemonic)</li>
 * </ul>
 *
 * <h2>Recovery Testing</h2>
 *
 * <h3>Test Recovery Procedure</h3>
 * <pre>{@code
 * // 1. Generate test mnemonic
 * String mnemonic = MnemonicManager.generateMnemonic();
 * System.out.println("Write down this mnemonic: " + mnemonic);
 *
 * // 2. Create test wallet
 * DeterministicKey masterKey = MnemonicManager.loadFromMnemonic(mnemonic, "");
 * // ... fund wallet with small amount ...
 *
 * // 3. Destroy digital copy
 * mnemonic = null; // Simulate losing device
 *
 * // 4. Recover from backup
 * String recoveredMnemonic = readFromBackup(); // User reads paper backup
 * DeterministicKey recoveredKey = MnemonicManager.loadFromMnemonic(recoveredMnemonic, "");
 *
 * // 5. Verify access to funds
 * if (recoveredKey.getPrivateKeyAsHex().equals(masterKey.getPrivateKeyAsHex())) {
 *     System.out.println("✓ Recovery successful!");
 * } else {
 *     System.out.println("✗ Recovery failed - backup is incorrect!");
 * }
 * }</pre>
 *
 * <h2>Additional Resources</h2>
 * <ul>
 *   <li><a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a></li>
 *   <li><a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13 Deterministic Secrets</a></li>
 *   <li><a href="https://www.eff.org/dice">EFF Diceware Passphrases</a></li>
 *   <li>{@link MnemonicManager} - Mnemonic generation and validation utilities</li>
 * </ul>
 *
 * <h2>Disclaimer</h2>
 * <p>These recommendations represent current best practices but cannot guarantee absolute
 * security. Users must assess their own threat model and adjust security measures accordingly.
 * The Cashu protocol and this implementation are experimental software. Use at your own risk.
 *
 * @see MnemonicManager
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
public final class SecureStorageRecommendations {

    /**
     * Private constructor prevents instantiation of documentation class.
     */
    private SecureStorageRecommendations() {
        throw new AssertionError(
            "SecureStorageRecommendations is a documentation class and should not be instantiated"
        );
    }
}
