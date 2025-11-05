package xyz.tcheeric.cashu.wallet.proto.state;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link DerivationStateManager}.
 *
 * <p>This implementation stores all state in memory using concurrent data structures
 * for thread safety. State is lost when the JVM terminates.
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe using:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for keyset storage</li>
 *   <li>{@link AtomicInteger} for counter values</li>
 *   <li>{@link Collections#synchronizedSet} for mint records</li>
 * </ul>
 *
 * <h2>Memory Considerations</h2>
 * <p>This implementation stores all mint records in memory. For wallets with many
 * minted tokens, memory usage grows linearly with the number of mints.
 *
 * <p><b>Estimated Memory:</b> ~40 bytes per mint record (overhead + Integer)
 * <ul>
 *   <li>1,000 mints ≈ 40 KB</li>
 *   <li>10,000 mints ≈ 400 KB</li>
 *   <li>100,000 mints ≈ 4 MB</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * <p>This implementation does NOT persist state. For production use, consider:
 * <ul>
 *   <li>Extending this class and overriding methods to add persistence</li>
 *   <li>Implementing a separate persisted version</li>
 *   <li>Periodically exporting state via {@link #exportState()} and restoring via {@link #importState(Map)}</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DerivationStateManager manager = new InMemoryDerivationStateManager();
 *
 * KeysetId keysetId = new KeysetId("009a1f293253e41e");
 *
 * // Mint first token
 * int counter = manager.getCurrentCounter(keysetId); // 0
 * manager.recordMint(keysetId, counter);
 * manager.incrementCounter(keysetId);
 *
 * // Mint second token
 * counter = manager.getCurrentCounter(keysetId); // 1
 * manager.recordMint(keysetId, counter);
 * manager.incrementCounter(keysetId);
 *
 * // Check state
 * int mintCount = manager.getMintCount(keysetId); // 2
 * int highest = manager.getHighestRecordedCounter(keysetId); // 1
 * List<Integer> gaps = manager.detectGaps(keysetId); // []
 * }</pre>
 *
 * @see DerivationStateManager
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@Nut(13)
@Slf4j
public class InMemoryDerivationStateManager implements DerivationStateManager {

    /**
     * Stores current counter for each keyset.
     * Key: KeysetId string representation
     * Value: AtomicInteger for thread-safe counter updates
     */
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Stores mint records for each keyset.
     * Key: KeysetId string representation
     * Value: Synchronized set of counter values with recorded mints
     */
    private final ConcurrentHashMap<String, Set<Integer>> mintRecords = new ConcurrentHashMap<>();

    @Override
    public int getCurrentCounter(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();
        AtomicInteger counter = counters.get(key);

        if (counter == null) {
            log.debug("derivation_state get_counter keyset={} counter=0 reason=new_keyset", keysetId);
            return 0;
        }

        int value = counter.get();
        log.debug("derivation_state get_counter keyset={} counter={}", keysetId, value);
        return value;
    }

    @Override
    public void incrementCounter(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();

        int newValue = counters.computeIfAbsent(key, k -> new AtomicInteger(0))
                              .incrementAndGet();

        log.info("derivation_state increment_counter keyset={} new_counter={}", keysetId, newValue);
    }

    @Override
    public void setCounter(@NonNull KeysetId keysetId, int counter) {
        if (counter < 0) {
            throw new IllegalArgumentException("Counter must be non-negative, got: " + counter);
        }

        String key = keysetId.toString();
        int oldValue = counters.computeIfAbsent(key, k -> new AtomicInteger(0))
                              .getAndSet(counter);

        if (counter < oldValue) {
            log.warn("derivation_state set_counter keyset={} old_counter={} new_counter={} " +
                    "warning=counter_decreased risk=potential_key_reuse",
                    keysetId, oldValue, counter);
        } else {
            log.info("derivation_state set_counter keyset={} old_counter={} new_counter={}",
                    keysetId, oldValue, counter);
        }
    }

    @Override
    public void recordMint(@NonNull KeysetId keysetId, int counter) {
        if (counter < 0) {
            throw new IllegalArgumentException("Counter must be non-negative, got: " + counter);
        }

        String key = keysetId.toString();

        Set<Integer> records = mintRecords.computeIfAbsent(key,
                k -> Collections.synchronizedSet(new HashSet<>()));

        boolean added = records.add(counter);

        if (added) {
            log.info("derivation_state record_mint keyset={} counter={} total_mints={}",
                    keysetId, counter, records.size());
        } else {
            log.debug("derivation_state record_mint keyset={} counter={} status=duplicate",
                    keysetId, counter);
        }
    }

    @Override
    public List<Integer> detectGaps(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();

        int currentCounter = getCurrentCounter(keysetId);
        Set<Integer> records = mintRecords.get(key);

        if (records == null || records.isEmpty()) {
            log.debug("derivation_state detect_gaps keyset={} gaps=0 reason=no_mints", keysetId);
            return List.of();
        }

        // Find all counter values from 0 to current that don't have mints
        List<Integer> gaps = new ArrayList<>();

        // Thread-safe iteration with synchronized set
        synchronized (records) {
            for (int i = 0; i < currentCounter; i++) {
                if (!records.contains(i)) {
                    gaps.add(i);
                }
            }
        }

        if (!gaps.isEmpty()) {
            log.warn("derivation_state detect_gaps keyset={} gaps={} gap_count={} " +
                    "current_counter={} total_mints={}",
                    keysetId, gaps, gaps.size(), currentCounter, records.size());
        } else {
            log.debug("derivation_state detect_gaps keyset={} gaps=0", keysetId);
        }

        return gaps;
    }

    @Override
    public int getHighestRecordedCounter(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();
        Set<Integer> records = mintRecords.get(key);

        if (records == null || records.isEmpty()) {
            log.debug("derivation_state get_highest_counter keyset={} highest=-1 reason=no_mints",
                    keysetId);
            return -1;
        }

        // Thread-safe iteration
        synchronized (records) {
            int highest = records.stream()
                    .max(Integer::compareTo)
                    .orElse(-1);

            log.debug("derivation_state get_highest_counter keyset={} highest={}",
                    keysetId, highest);

            return highest;
        }
    }

    @Override
    public boolean hasMintRecord(@NonNull KeysetId keysetId, int counter) {
        if (counter < 0) {
            throw new IllegalArgumentException("Counter must be non-negative, got: " + counter);
        }

        String key = keysetId.toString();
        Set<Integer> records = mintRecords.get(key);

        boolean has = records != null && records.contains(counter);

        log.debug("derivation_state has_mint_record keyset={} counter={} has_record={}",
                keysetId, counter, has);

        return has;
    }

    @Override
    public int getMintCount(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();
        Set<Integer> records = mintRecords.get(key);

        int count = records != null ? records.size() : 0;

        log.debug("derivation_state get_mint_count keyset={} count={}", keysetId, count);

        return count;
    }

    @Override
    public void clearKeyset(@NonNull KeysetId keysetId) {
        String key = keysetId.toString();

        AtomicInteger oldCounter = counters.remove(key);
        Set<Integer> oldRecords = mintRecords.remove(key);

        int oldCounterValue = oldCounter != null ? oldCounter.get() : 0;
        int oldMintCount = oldRecords != null ? oldRecords.size() : 0;

        log.warn("derivation_state clear_keyset keyset={} old_counter={} old_mint_count={} " +
                "action=state_destroyed",
                keysetId, oldCounterValue, oldMintCount);
    }

    @Override
    public void clearAll() {
        int keysetCount = counters.size();
        int totalMints = mintRecords.values().stream()
                .mapToInt(Set::size)
                .sum();

        counters.clear();
        mintRecords.clear();

        log.warn("derivation_state clear_all keysets={} total_mints={} action=all_state_destroyed",
                keysetCount, totalMints);
    }

    /**
     * Exports the current state for all keysets.
     *
     * <p>This method creates a snapshot of current counters and mint records that can be
     * persisted and later restored via {@link #importState(Map)}.
     *
     * <p><b>Use Case:</b> Implementing manual persistence by periodically calling this
     * method and saving the result to disk.
     *
     * @return Map of keyset ID to state snapshot
     */
    public Map<String, KeysetState> exportState() {
        Map<String, KeysetState> state = new HashMap<>();

        for (String keysetId : counters.keySet()) {
            int counter = counters.get(keysetId).get();
            Set<Integer> records = mintRecords.get(keysetId);

            // Create defensive copy of mint records
            Set<Integer> recordsCopy = records != null
                    ? new HashSet<>(records)
                    : new HashSet<>();

            state.put(keysetId, new KeysetState(counter, recordsCopy));
        }

        log.info("derivation_state export_state keysets={} total_mints={}",
                state.size(),
                state.values().stream().mapToInt(s -> s.mintRecords.size()).sum());

        return state;
    }

    /**
     * Imports state from a previous export.
     *
     * <p>This method restores counters and mint records from a state snapshot.
     * Existing state is cleared before import.
     *
     * <p><b>Warning:</b> This clears all existing state. Only use when restoring
     * from a known-good backup.
     *
     * @param state Map of keyset ID to state snapshot
     * @throws IllegalArgumentException if state is null
     */
    public void importState(@NonNull Map<String, KeysetState> state) {
        clearAll();

        for (Map.Entry<String, KeysetState> entry : state.entrySet()) {
            String keysetId = entry.getKey();
            KeysetState keysetState = entry.getValue();

            counters.put(keysetId, new AtomicInteger(keysetState.counter));

            Set<Integer> records = Collections.synchronizedSet(
                    new HashSet<>(keysetState.mintRecords)
            );
            mintRecords.put(keysetId, records);
        }

        log.info("derivation_state import_state keysets={} total_mints={}",
                state.size(),
                state.values().stream().mapToInt(s -> s.mintRecords.size()).sum());
    }

    /**
     * Snapshot of state for a single keyset.
     */
    public static class KeysetState {
        public final int counter;
        public final Set<Integer> mintRecords;

        public KeysetState(int counter, Set<Integer> mintRecords) {
            this.counter = counter;
            this.mintRecords = mintRecords;
        }
    }
}
