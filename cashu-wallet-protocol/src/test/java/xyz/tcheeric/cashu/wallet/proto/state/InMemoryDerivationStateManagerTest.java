package xyz.tcheeric.cashu.wallet.proto.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.tcheeric.cashu.common.KeysetId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link InMemoryDerivationStateManager}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Counter management (get, set, increment)</li>
 *   <li>Mint record tracking</li>
 *   <li>Gap detection</li>
 *   <li>State management (clear, export, import)</li>
 *   <li>Thread safety</li>
 *   <li>Edge cases and error handling</li>
 * </ul>
 *
 * @author NUT-13 Implementation Team
 * @since 0.1.4
 */
@DisplayName("InMemoryDerivationStateManager Tests")
class InMemoryDerivationStateManagerTest {

    private InMemoryDerivationStateManager manager;
    private KeysetId keysetId;

    @BeforeEach
    void setUp() {
        manager = new InMemoryDerivationStateManager();
        keysetId = new KeysetId("009a1f293253e41e");
    }

    // ========================================
    // Counter Management Tests
    // ========================================

    @Test
    @DisplayName("Should return 0 for new keyset")
    void testGetCurrentCounterForNewKeyset() {
        int counter = manager.getCurrentCounter(keysetId);
        assertEquals(0, counter);
    }

    @Test
    @DisplayName("Should increment counter correctly")
    void testIncrementCounter() {
        assertEquals(0, manager.getCurrentCounter(keysetId));

        manager.incrementCounter(keysetId);
        assertEquals(1, manager.getCurrentCounter(keysetId));

        manager.incrementCounter(keysetId);
        assertEquals(2, manager.getCurrentCounter(keysetId));

        manager.incrementCounter(keysetId);
        assertEquals(3, manager.getCurrentCounter(keysetId));
    }

    @Test
    @DisplayName("Should set counter to specific value")
    void testSetCounter() {
        manager.setCounter(keysetId, 10);
        assertEquals(10, manager.getCurrentCounter(keysetId));

        manager.setCounter(keysetId, 20);
        assertEquals(20, manager.getCurrentCounter(keysetId));
    }

    @Test
    @DisplayName("Should throw exception for negative counter")
    void testSetNegativeCounter() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.setCounter(keysetId, -1)
        );

        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    @DisplayName("Should throw exception for null keysetId in getCurrentCounter")
    void testGetCurrentCounterWithNull() {
        assertThrows(NullPointerException.class,
            () -> manager.getCurrentCounter(null));
    }

    @Test
    @DisplayName("Should throw exception for null keysetId in incrementCounter")
    void testIncrementCounterWithNull() {
        assertThrows(NullPointerException.class,
            () -> manager.incrementCounter(null));
    }

    @Test
    @DisplayName("Should throw exception for null keysetId in setCounter")
    void testSetCounterWithNull() {
        assertThrows(NullPointerException.class,
            () -> manager.setCounter(null, 10));
    }

    // ========================================
    // Mint Record Tests
    // ========================================

    @Test
    @DisplayName("Should record mint successfully")
    void testRecordMint() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.recordMint(keysetId, 2);

        assertTrue(manager.hasMintRecord(keysetId, 0));
        assertTrue(manager.hasMintRecord(keysetId, 1));
        assertTrue(manager.hasMintRecord(keysetId, 2));
        assertFalse(manager.hasMintRecord(keysetId, 3));
    }

    @Test
    @DisplayName("Should handle duplicate mint records")
    void testRecordDuplicateMint() {
        manager.recordMint(keysetId, 5);
        manager.recordMint(keysetId, 5); // Duplicate

        assertTrue(manager.hasMintRecord(keysetId, 5));
        assertEquals(1, manager.getMintCount(keysetId)); // Should only count once
    }

    @Test
    @DisplayName("Should throw exception for negative counter in recordMint")
    void testRecordMintWithNegativeCounter() {
        assertThrows(IllegalArgumentException.class,
            () -> manager.recordMint(keysetId, -1));
    }

    @Test
    @DisplayName("Should throw exception for null keysetId in recordMint")
    void testRecordMintWithNull() {
        assertThrows(NullPointerException.class,
            () -> manager.recordMint(null, 0));
    }

    @Test
    @DisplayName("Should return correct mint count")
    void testGetMintCount() {
        assertEquals(0, manager.getMintCount(keysetId));

        manager.recordMint(keysetId, 0);
        assertEquals(1, manager.getMintCount(keysetId));

        manager.recordMint(keysetId, 1);
        assertEquals(2, manager.getMintCount(keysetId));

        manager.recordMint(keysetId, 5);
        assertEquals(3, manager.getMintCount(keysetId));
    }

    @Test
    @DisplayName("Should return 0 mint count for new keyset")
    void testGetMintCountForNewKeyset() {
        assertEquals(0, manager.getMintCount(keysetId));
    }

    // ========================================
    // Gap Detection Tests
    // ========================================

    @Test
    @DisplayName("Should detect no gaps when all counters are sequential")
    void testDetectGapsNoGaps() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.recordMint(keysetId, 2);
        manager.setCounter(keysetId, 3);

        List<Integer> gaps = manager.detectGaps(keysetId);
        assertTrue(gaps.isEmpty());
    }

    @Test
    @DisplayName("Should detect single gap")
    void testDetectSingleGap() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        // Gap at 2
        manager.recordMint(keysetId, 3);
        manager.setCounter(keysetId, 4);

        List<Integer> gaps = manager.detectGaps(keysetId);
        assertEquals(List.of(2), gaps);
    }

    @Test
    @DisplayName("Should detect multiple gaps")
    void testDetectMultipleGaps() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        // Gap at 2
        manager.recordMint(keysetId, 3);
        // Gap at 4
        manager.recordMint(keysetId, 5);
        manager.recordMint(keysetId, 6);
        // Gap at 7
        manager.setCounter(keysetId, 8);

        List<Integer> gaps = manager.detectGaps(keysetId);
        assertEquals(List.of(2, 4, 7), gaps);
    }

    @Test
    @DisplayName("Should return empty list when no mints recorded")
    void testDetectGapsNoMints() {
        List<Integer> gaps = manager.detectGaps(keysetId);
        assertTrue(gaps.isEmpty());
    }

    @Test
    @DisplayName("Should detect gaps only up to current counter")
    void testDetectGapsUpToCurrentCounter() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 5);  // Gap from 1-4
        manager.recordMint(keysetId, 10); // Gap from 6-9, but counter is still low
        manager.setCounter(keysetId, 6);  // Only check up to 6

        List<Integer> gaps = manager.detectGaps(keysetId);
        assertEquals(List.of(1, 2, 3, 4, 5), gaps);
        // 6-9 are not gaps because current counter is 6
    }

    // ========================================
    // Highest Counter Tests
    // ========================================

    @Test
    @DisplayName("Should return -1 for keyset with no mints")
    void testGetHighestRecordedCounterNoMints() {
        assertEquals(-1, manager.getHighestRecordedCounter(keysetId));
    }

    @Test
    @DisplayName("Should return highest recorded counter")
    void testGetHighestRecordedCounter() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 5);
        manager.recordMint(keysetId, 3);
        manager.recordMint(keysetId, 10);

        assertEquals(10, manager.getHighestRecordedCounter(keysetId));
    }

    @Test
    @DisplayName("Highest recorded may differ from current counter")
    void testHighestRecordedVsCurrentCounter() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.recordMint(keysetId, 2);
        manager.setCounter(keysetId, 10); // Set counter ahead

        assertEquals(2, manager.getHighestRecordedCounter(keysetId));
        assertEquals(10, manager.getCurrentCounter(keysetId));
    }

    // ========================================
    // State Management Tests
    // ========================================

    @Test
    @DisplayName("Should clear keyset state")
    void testClearKeyset() {
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.setCounter(keysetId, 2);

        assertEquals(2, manager.getCurrentCounter(keysetId));
        assertEquals(2, manager.getMintCount(keysetId));

        manager.clearKeyset(keysetId);

        assertEquals(0, manager.getCurrentCounter(keysetId));
        assertEquals(0, manager.getMintCount(keysetId));
        assertFalse(manager.hasMintRecord(keysetId, 0));
        assertFalse(manager.hasMintRecord(keysetId, 1));
    }

    @Test
    @DisplayName("Should clear all state")
    void testClearAll() {
        KeysetId keyset1 = new KeysetId("009a1f293253e41e");
        KeysetId keyset2 = new KeysetId("009a1f293253e41f");

        manager.recordMint(keyset1, 0);
        manager.setCounter(keyset1, 1);

        manager.recordMint(keyset2, 0);
        manager.setCounter(keyset2, 1);

        manager.clearAll();

        assertEquals(0, manager.getCurrentCounter(keyset1));
        assertEquals(0, manager.getCurrentCounter(keyset2));
        assertEquals(0, manager.getMintCount(keyset1));
        assertEquals(0, manager.getMintCount(keyset2));
    }

    @Test
    @DisplayName("Should export and import state correctly")
    void testExportImportState() {
        // Setup state
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.recordMint(keysetId, 2);
        manager.setCounter(keysetId, 3);

        // Export
        Map<String, InMemoryDerivationStateManager.KeysetState> exported = manager.exportState();

        // Create new manager and import
        InMemoryDerivationStateManager newManager = new InMemoryDerivationStateManager();
        newManager.importState(exported);

        // Verify state matches
        assertEquals(3, newManager.getCurrentCounter(keysetId));
        assertEquals(3, newManager.getMintCount(keysetId));
        assertTrue(newManager.hasMintRecord(keysetId, 0));
        assertTrue(newManager.hasMintRecord(keysetId, 1));
        assertTrue(newManager.hasMintRecord(keysetId, 2));
    }

    @Test
    @DisplayName("Should handle export of multiple keysets")
    void testExportMultipleKeysets() {
        KeysetId keyset1 = new KeysetId("009a1f293253e41e");
        KeysetId keyset2 = new KeysetId("009a1f293253e41f");

        manager.recordMint(keyset1, 0);
        manager.setCounter(keyset1, 1);

        manager.recordMint(keyset2, 0);
        manager.recordMint(keyset2, 1);
        manager.setCounter(keyset2, 2);

        Map<String, InMemoryDerivationStateManager.KeysetState> exported = manager.exportState();

        assertEquals(2, exported.size());
        assertTrue(exported.containsKey(keyset1.toString()));
        assertTrue(exported.containsKey(keyset2.toString()));
    }

    @Test
    @DisplayName("Import should clear existing state")
    void testImportClearsExistingState() {
        KeysetId existingKeyset = new KeysetId("existing");
        manager.recordMint(existingKeyset, 0);
        manager.setCounter(existingKeyset, 1);

        // Import state for different keyset
        InMemoryDerivationStateManager.KeysetState newState =
            new InMemoryDerivationStateManager.KeysetState(5, Set.of(0, 1, 2, 3, 4));

        manager.importState(Map.of(keysetId.toString(), newState));

        // Existing keyset should be gone
        assertEquals(0, manager.getCurrentCounter(existingKeyset));
        assertEquals(0, manager.getMintCount(existingKeyset));

        // New keyset should be present
        assertEquals(5, manager.getCurrentCounter(keysetId));
        assertEquals(5, manager.getMintCount(keysetId));
    }

    // ========================================
    // Multiple Keyset Tests
    // ========================================

    @Test
    @DisplayName("Should handle multiple keysets independently")
    void testMultipleKeysets() {
        KeysetId keyset1 = new KeysetId("009a1f293253e41e");
        KeysetId keyset2 = new KeysetId("009a1f293253e41f");

        manager.recordMint(keyset1, 0);
        manager.recordMint(keyset1, 1);
        manager.setCounter(keyset1, 2);

        manager.recordMint(keyset2, 0);
        manager.setCounter(keyset2, 1);

        assertEquals(2, manager.getCurrentCounter(keyset1));
        assertEquals(2, manager.getMintCount(keyset1));

        assertEquals(1, manager.getCurrentCounter(keyset2));
        assertEquals(1, manager.getMintCount(keyset2));
    }

    // ========================================
    // Thread Safety Tests
    // ========================================

    @Test
    @DisplayName("Should handle concurrent increments safely")
    void testConcurrentIncrements() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        manager.incrementCounter(keysetId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * incrementsPerThread, manager.getCurrentCounter(keysetId));
    }

    @Test
    @DisplayName("Should handle concurrent mint records safely")
    void testConcurrentMintRecords() throws InterruptedException {
        int threadCount = 10;
        int mintsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger counterSource = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < mintsPerThread; j++) {
                        int counter = counterSource.getAndIncrement();
                        manager.recordMint(keysetId, counter);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount * mintsPerThread, manager.getMintCount(keysetId));
    }

    // ========================================
    // Integration Scenarios
    // ========================================

    @Test
    @DisplayName("Should support typical mint workflow")
    void testTypicalMintWorkflow() {
        // Mint 5 tokens sequentially
        for (int i = 0; i < 5; i++) {
            int counter = manager.getCurrentCounter(keysetId);
            assertEquals(i, counter);

            manager.recordMint(keysetId, counter);
            manager.incrementCounter(keysetId);
        }

        assertEquals(5, manager.getCurrentCounter(keysetId));
        assertEquals(5, manager.getMintCount(keysetId));
        assertEquals(4, manager.getHighestRecordedCounter(keysetId));
        assertTrue(manager.detectGaps(keysetId).isEmpty());
    }

    @Test
    @DisplayName("Should support recovery scenario with gaps")
    void testRecoveryScenarioWithGaps() {
        // Simulate partial recovery: found mints at counters 0, 1, 5, 6, 7
        manager.recordMint(keysetId, 0);
        manager.recordMint(keysetId, 1);
        manager.recordMint(keysetId, 5);
        manager.recordMint(keysetId, 6);
        manager.recordMint(keysetId, 7);
        manager.setCounter(keysetId, 8);

        // Check for gaps
        List<Integer> gaps = manager.detectGaps(keysetId);
        assertEquals(List.of(2, 3, 4), gaps);

        // Fill gaps
        for (int gap : gaps) {
            manager.recordMint(keysetId, gap);
        }

        // Verify gaps filled
        assertTrue(manager.detectGaps(keysetId).isEmpty());
        assertEquals(8, manager.getMintCount(keysetId));
    }
}
