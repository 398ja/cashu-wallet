package xyz.tcheeric.cashu.wallet.proto.mnemonic;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.crypto.DeterministicKey;

import java.util.Arrays;

/**
 * Holds a mnemonic for the shortest time that still lets a master key be derived from it.
 *
 * <h2>Why a class rather than a method</h2>
 *
 * <p>{@link MnemonicManager#loadFromMnemonic} takes a {@code String}. A String cannot be cleared:
 * it is immutable, it may be interned, and the caller holds a reference the callee cannot reach.
 * So the seed phrase stays resident until the garbage collector happens to reclaim it, which is
 * what audit finding M-18 (and SW-04 before it) is about. The earlier remediation zeroed the
 * derived blinding factors and left the mnemonic and master key alone, which is why the finding
 * stayed open after being marked fixed.
 *
 * <p>The only way to bound that lifetime is to never put the phrase in a String. This class takes
 * a {@code char[]}, derives the key, and clears the array. Callers that read a mnemonic from a
 * console, a file or a byte buffer can hand the characters straight over:
 *
 * <pre>{@code
 * char[] phrase = console.readPassword("mnemonic: ");
 * try (var seed = MnemonicSeed.of(phrase)) {
 *     DeterministicKey masterKey = seed.deriveMasterKey("");
 *     // ... use masterKey ...
 * } // phrase is zeroed here, whatever happened in between
 * }</pre>
 *
 * <h2>What this does not achieve</h2>
 *
 * <p>The JVM may still have copied the characters during derivation, and the derived
 * {@link DeterministicKey} holds key material this class does not own. Zeroing narrows the window
 * and removes the most obvious copy; it does not make the secret unrecoverable from a heap dump
 * taken at the wrong moment. Deployments that need that guarantee want an HSM, which is why
 * heap dumps are also now off by default in the services that hold key material.
 */
@Slf4j
public final class MnemonicSeed implements AutoCloseable {

    private final char[] phrase;
    private boolean closed;

    private MnemonicSeed(char[] phrase) {
        this.phrase = phrase;
    }

    /**
     * Takes ownership of the given characters.
     *
     * <p>The array is <em>not</em> copied: the caller is handing it over, and copying would mean
     * two arrays to clear instead of one. Do not reuse it afterwards.
     */
    public static MnemonicSeed of(@NonNull char[] phrase) {
        return new MnemonicSeed(phrase);
    }

    /**
     * Derives the BIP32 master key.
     *
     * @param passphrase the BIP39 passphrase, empty when unused
     * @return the derived master key
     * @throws IllegalStateException if this seed has already been closed
     */
    public DeterministicKey deriveMasterKey(@NonNull String passphrase) {
        if (closed) {
            throw new IllegalStateException("This mnemonic has been cleared");
        }
        // A String is unavoidable at the boundary, because the underlying BIP39 library takes
        // one. It is confined to this method rather than living for the caller's lifetime, which
        // is the difference the class exists to make.
        String asString = new String(phrase);
        try {
            return MnemonicManager.loadFromMnemonic(asString, passphrase);
        } finally {
            // Nothing useful can be done about `asString` itself; see the class note.
            close();
        }
    }

    /** Whether this seed has been cleared. */
    public boolean isCleared() {
        return closed;
    }

    /**
     * Zeroes the phrase. Idempotent, so try-with-resources on an already-derived seed is fine.
     */
    @Override
    public void close() {
        if (!closed) {
            Arrays.fill(phrase, '\0');
            closed = true;
        }
    }
}
