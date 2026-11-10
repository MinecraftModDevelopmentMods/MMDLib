package zone.moddev.mc.lib.gui.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuRequestsTest {
    @Test
    void onlyOneRequestCanBePendingAndExactAcknowledgmentsCannotUnlockRetries() {
        var requests = new MenuRequests.Client(20);
        assertEquals(1, requests.begin());
        assertEquals(0, requests.begin());
        assertFalse(requests.acknowledgeExact(0));
        assertFalse(requests.acknowledgeExact(2));
        assertTrue(requests.pending());
        assertTrue(requests.acknowledgeExact(1));
        assertEquals(2, requests.begin());
        assertFalse(requests.acknowledgeExact(1));
        assertTrue(requests.pending());
        assertTrue(requests.acknowledgeExact(2));
        assertEquals(3, requests.begin());
    }

    @Test
    void timeoutBoundaryReleasesOnceWithoutReusingIds() {
        for (int timeout : new int[]{20, 100}) {
            var requests = new MenuRequests.Client(timeout);
            assertFalse(requests.tick());
            assertEquals(1, requests.begin());
            for (int tick = 1; tick < timeout; tick++)
                assertFalse(requests.tick());
            assertTrue(requests.pending());
            assertTrue(requests.tick());
            assertFalse(requests.pending());
            assertFalse(requests.tick());
            assertEquals(2, requests.begin());
            assertFalse(requests.acknowledgeExact(1));
            for (int tick = 1; tick < timeout; tick++)
                assertFalse(requests.tick());
            assertTrue(requests.tick());
        }
    }

    @Test
    void cumulativeSnapshotsAdvanceSequenceWithoutClearingNewerPendingRequests() {
        var requests = new MenuRequests.Client(20);
        assertTrue(requests.acknowledgeThrough(42));
        assertEquals(43, requests.begin());
        assertFalse(requests.acknowledgeThrough(42));
        assertTrue(requests.pending());
        assertTrue(requests.acknowledgeThrough(45));
        assertFalse(requests.pending());
        assertEquals(46, requests.begin());
        assertFalse(requests.acknowledgeThrough(43));
        assertTrue(requests.pending());
    }

    @Test
    void delayedCumulativeAcknowledgmentCannotUnlockRecoveryRequest() {
        var requests = new MenuRequests.Client(1);
        int original = requests.begin();
        assertTrue(requests.tick());
        int recovery = requests.begin();
        assertFalse(requests.acknowledgeThrough(original));
        assertTrue(requests.pending());
        assertTrue(requests.acknowledgeThrough(recovery));
        assertFalse(requests.pending());
    }

    @Test
    void exhaustedSequenceNeverWrapsEvenAfterTimeout() {
        var requests = new MenuRequests.Client(1);
        requests.acknowledgeThrough(Integer.MAX_VALUE - 1);
        assertEquals(Integer.MAX_VALUE, requests.begin());
        assertTrue(requests.tick());
        assertEquals(0, requests.begin());
        requests.acknowledgeThrough(1);
        assertEquals(0, requests.begin());
    }

    @Test
    void replaysDoNotSpendAllowanceButRateRejectedIdsStayConsumed() {
        for (int limit : new int[]{8, 16, 32}) {
            var requests = new MenuRequests.Server(limit);
            assertFalse(requests.receive(0));
            assertFalse(requests.receive(-1));
            for (int request = 1; request <= limit; request++) {
                assertTrue(requests.receive(request));
                assertTrue(requests.allow(7));
                assertFalse(requests.receive(request));
            }
            assertTrue(requests.receive(limit + 1));
            assertFalse(requests.allow(7));
            assertEquals(limit + 1, requests.acknowledged());
            assertFalse(requests.receive(limit + 1));
            assertTrue(requests.receive(limit + 2));
            assertTrue(requests.allow(8));
            assertTrue(requests.receive(limit + 3));
            assertTrue(requests.allow(0));
            assertTrue(requests.receive(Integer.MAX_VALUE));
            assertFalse(requests.receive(Integer.MIN_VALUE));
            assertEquals(Integer.MAX_VALUE, requests.acknowledged());
        }
    }

    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new MenuRequests.Client(0));
        assertThrows(IllegalArgumentException.class,
            () -> new MenuRequests.Server(0));
    }
}
