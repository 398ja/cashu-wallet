package xyz.tcheeric.cashu.wallet.proto.state;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.List;

/**
 * Manages derivation counter state for NUT-13 deterministic wallets.
 *
 * <p>In NUT-13, each keyset maintains a counter that increments with each token minted.
 * This interface provides operations for tracking, updating, and analyzing counter state
 * to support efficient wallet recovery and gap detection.
 *
 * <h2>Purpose</h2>
 * <ul>
 *   <li>Track current counter value for each keyset</li>
 *   <li>Record successful mint operations at specific counter values</li>
 *   <li>Detect gaps in counter sequences for recovery optimization</li>
 *   <li>Persist state across wallet sessions</li>
 * </ul>
 *
 * <h2>NUT-13 Counter Semantics</h2>
 * <p>Per NUT-13 specification:
 * <ul>
 *   <li>Counter starts at 0 for new keysets</li>
 *   <li>Counter increments by 1 for each token minted</li>
 *   <li>Same counter value always produces same secret for a given keyset</li>
 *   <li>Recovery scans in batches (typically 100) looking for used counters</li>
 *   <li>3 consecutive empty batches signal end of recovery</li>
 * </ul>
 *
 * <h2>Gap Detection</h2>
 * <p>Gaps occur when:
 * <ul>
 *   <li>User mints tokens but some fail (network issues, insufficient funds)</li>
 *   <li>Wallet state is restored from partial backup</li>
 *   <li>Multiple devices mint tokens concurrently</li>
 * </ul>
 * <p>Example gap: Used counters [0, 1, 2, 5, 6, 7] has gap at [3, 4]
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DerivationStateManager stateManager = new InMemoryDerivationStateManager();
 *
 * // Initialize new keyset
 * KeysetId keysetId = new KeysetId("009a1f293253e41e");
 * int counter = stateManager.getCurrentCounter(keysetId); // Returns 0 for new keyset
 *
 * // After successful mint
 * stateManager.recordMint(keysetId, counter);
 * stateManager.incrementCounter(keysetId);
 *
 * // During recovery, detect gaps
 * List<Integer> gaps = stateManager.detectGaps(keysetId);
 * if (!gaps.isEmpty()) {
 *     System.out.println("Found gaps at counters: " + gaps);
 *     // Recovery process should fill these gaps
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe to support concurrent wallet operations.
 *
 * <h2>Persistence</h2>
 * <p>Implementations should persist state to survive wallet restarts. Loss of counter
 * state may result in key reuse or inefficient recovery.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13 Specification</a>
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@Nut(13)
public interface DerivationStateManager {

    /**
     * Gets the current counter value for a keyset.
     *
     * <p>The current counter represents the next value to use for minting.
     * For new keysets (never seen before), this returns 0.
     *
     * <p><b>Thread Safety:</b> This method must be thread-safe.
     *
     * @param keysetId The keyset to query
     * @return Current counter value (≥ 0)
     * @throws IllegalArgumentException if keysetId is null
     */
    int getCurrentCounter(@NonNull KeysetId keysetId);

    /**
     * Increments the counter for a keyset by 1.
     *
     * <p>This should be called after successfully using a counter value for minting.
     * The counter advances from N to N+1.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * int counter = stateManager.getCurrentCounter(keysetId); // 5
     * // ... use counter 5 to mint token ...
     * stateManager.incrementCounter(keysetId);
     * int newCounter = stateManager.getCurrentCounter(keysetId); // 6
     * }</pre>
     *
     * <p><b>Thread Safety:</b> This method must be thread-safe and atomic.
     *
     * @param keysetId The keyset to increment
     * @throws IllegalArgumentException if keysetId is null
     */
    void incrementCounter(@NonNull KeysetId keysetId);

    /**
     * Sets the counter for a keyset to a specific value.
     *
     * <p>Use this method to:
     * <ul>
     *   <li>Initialize counter from persisted state</li>
     *   <li>Update counter after recovery detects higher used values</li>
     *   <li>Reset counter for testing purposes</li>
     * </ul>
     *
     * <p><b>Warning:</b> Setting counter to a value lower than previously used may
     * result in key reuse, which breaks NUT-13 security guarantees.
     *
     * @param keysetId The keyset to update
     * @param counter The new counter value (must be ≥ 0)
     * @throws IllegalArgumentException if keysetId is null or counter is negative
     */
    void setCounter(@NonNull KeysetId keysetId, int counter);

    /**
     * Records a successful mint operation at a specific counter value.
     *
     * <p>This creates a history record indicating that the given counter was used
     * to mint a token. This information is used for gap detection and recovery
     * optimization.
     *
     * <p><b>Note:</b> Recording a mint does NOT automatically increment the counter.
     * Callers should call {@link #incrementCounter(KeysetId)} separately.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * int counter = stateManager.getCurrentCounter(keysetId);
     * // ... derive secret at counter ...
     * // ... successfully mint token ...
     * stateManager.recordMint(keysetId, counter);
     * stateManager.incrementCounter(keysetId);
     * }</pre>
     *
     * @param keysetId The keyset that was used
     * @param counter The counter value that was minted (must be ≥ 0)
     * @throws IllegalArgumentException if keysetId is null or counter is negative
     */
    void recordMint(@NonNull KeysetId keysetId, int counter);

    /**
     * Detects gaps in the counter sequence for a keyset.
     *
     * <p>A gap is a counter value between 0 and the current counter that has no
     * recorded mint operation. Gaps indicate potential lost tokens that should be
     * checked during recovery.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Recorded mints at counters: 0, 1, 2, 5, 6, 7
     * // Current counter: 8
     * List<Integer> gaps = stateManager.detectGaps(keysetId);
     * // Returns: [3, 4]
     * }</pre>
     *
     * <p><b>Recovery Strategy:</b>
     * During wallet recovery, gaps should be checked first before scanning ahead.
     * This minimizes unnecessary requests to the mint.
     *
     * <p><b>Performance:</b> For keysets with many counters, this may be expensive.
     * Implementations should consider caching or limiting gap detection range.
     *
     * @param keysetId The keyset to analyze
     * @return List of counter values with gaps, sorted ascending. Empty if no gaps.
     * @throws IllegalArgumentException if keysetId is null
     */
    List<Integer> detectGaps(@NonNull KeysetId keysetId);

    /**
     * Gets the highest recorded mint counter for a keyset.
     *
     * <p>This returns the maximum counter value that has a recorded mint operation.
     * For keysets with no mints, this returns -1.
     *
     * <p><b>Use Case:</b> During recovery, compare the highest recorded counter with
     * the highest counter found on the mint to detect potential lost state.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Recorded mints at counters: 0, 1, 2, 5, 6, 7
     * int highest = stateManager.getHighestRecordedCounter(keysetId); // Returns 7
     * }</pre>
     *
     * @param keysetId The keyset to query
     * @return Highest recorded counter value, or -1 if no mints recorded
     * @throws IllegalArgumentException if keysetId is null
     */
    int getHighestRecordedCounter(@NonNull KeysetId keysetId);

    /**
     * Checks if a specific counter value has a recorded mint.
     *
     * <p>This is useful for verifying whether a counter was previously used
     * before attempting to mint with it.
     *
     * @param keysetId The keyset to check
     * @param counter The counter value to check
     * @return true if counter has recorded mint, false otherwise
     * @throws IllegalArgumentException if keysetId is null or counter is negative
     */
    boolean hasMintRecord(@NonNull KeysetId keysetId, int counter);

    /**
     * Gets count of recorded mints for a keyset.
     *
     * <p>This returns the total number of counter values with recorded mints.
     * Note: This may differ from current counter if there are gaps.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Recorded mints at: 0, 1, 2, 5, 6, 7
     * // Current counter: 8
     * int mintCount = stateManager.getMintCount(keysetId); // Returns 6
     * int currentCounter = stateManager.getCurrentCounter(keysetId); // Returns 8
     * }</pre>
     *
     * @param keysetId The keyset to query
     * @return Number of recorded mints (≥ 0)
     * @throws IllegalArgumentException if keysetId is null
     */
    int getMintCount(@NonNull KeysetId keysetId);

    /**
     * Clears all state for a keyset.
     *
     * <p><b>Warning:</b> This operation is destructive and should only be used:
     * <ul>
     *   <li>During testing</li>
     *   <li>When explicitly resetting wallet state</li>
     *   <li>After verifying all tokens are spent or backed up</li>
     * </ul>
     *
     * <p>After clearing, the keyset will be treated as new (counter = 0, no mints).
     *
     * @param keysetId The keyset to clear
     * @throws IllegalArgumentException if keysetId is null
     */
    void clearKeyset(@NonNull KeysetId keysetId);

    /**
     * Clears all state for all keysets.
     *
     * <p><b>Warning:</b> This operation is extremely destructive. Use with caution.
     * Typically only used for testing or complete wallet reset.
     */
    void clearAll();
}
