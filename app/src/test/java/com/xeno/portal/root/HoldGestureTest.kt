package com.xeno.portal.root

import org.junit.Assert.*
import org.junit.Test

class HoldGestureTest {
    @Test fun quickTapNeverProducesHold() {
        val hold = HoldGesture(350, 8f)
        assertTrue(hold.down(10f, 20f, 1_000L))
        assertNull(hold.poll(1_100L))
        hold.cancel() // ACTION_UP
        assertNull(hold.poll(1_350L))
    }

    @Test fun stationaryFingerFiresAtThresholdExactlyOnce() {
        val hold = HoldGesture(350, 8f)
        hold.down(10f, 20f, 1_000L)
        assertNull(hold.poll(1_349L))
        assertEquals(HoldGesture.Hold(10f, 20f, 1_000L, 1_350L), hold.poll(1_350L))
        assertNull(hold.poll(1_351L))
        hold.cancel()
        assertNull(hold.poll(1_400L))
    }

    @Test fun movementOutsideSlopCannotBeUndoneByReturningToOrigin() {
        val hold = HoldGesture(350, 8f)
        hold.down(10f, 20f, 1_000L)
        assertFalse(hold.move(19f, 20f))
        assertFalse(hold.move(10f, 20f))
        assertNull(hold.poll(1_350L))
    }

    @Test fun slopUsesEuclideanDistanceAndAllowsItsBoundary() {
        val boundary = HoldGesture(350, 5f)
        boundary.down(0f, 0f, 1_000L)
        assertTrue(boundary.move(3f, 4f))
        assertNotNull(boundary.poll(1_350L))
        val outside = HoldGesture(350, 5f)
        outside.down(0f, 0f, 1_000L)
        assertFalse(outside.move(4f, 4f))
        assertNull(outside.poll(1_350L))
    }

    @Test fun secondPointerCancelsAndDoesNotRestartWhenItLeaves() {
        val hold = HoldGesture(350, 8f)
        hold.down(10f, 20f, 1_000L)
        assertFalse(hold.move(10f, 20f, pointerCount = 2))
        assertFalse(hold.move(10f, 20f, pointerCount = 1))
        assertNull(hold.poll(1_350L))
        assertFalse(hold.down(10f, 20f, 2_000L, pointerCount = 2))
    }

    @Test fun shortTimerDelayIsAllowedButStaleTimerIsRejected() {
        val allowed = HoldGesture(350, 8f)
        allowed.down(10f, 20f, 1_000L)
        assertNotNull(allowed.poll(1_450L))
        val stale = HoldGesture(350, 8f)
        stale.down(10f, 20f, 1_000L)
        assertNull(stale.poll(1_501L))
        assertFalse(stale.isActive)
        assertNull(stale.poll(1_502L))
    }

    @Test fun cancelAndNewDownDiscardOldTimerState() {
        val hold = HoldGesture(350, 8f)
        hold.down(10f, 20f, 1_000L)
        hold.cancel() // CANCEL, focus loss, Activity pause, or settings disabled.
        assertNull(hold.poll(1_350L))
        hold.down(30f, 40f, 2_000L)
        assertNull(hold.poll(2_349L))
        assertEquals(30f, hold.poll(2_350L)!!.x)
    }

    @Test fun newDownStartsItsOwnDeadlineAndCoordinates() {
        val hold = HoldGesture(250, 8f)
        hold.down(10f, 20f, 1_000L)
        hold.down(30f, 40f, 1_200L)
        assertNull(hold.poll(1_250L))
        assertEquals(HoldGesture.Hold(30f, 40f, 1_200L, 1_450L), hold.poll(1_450L))
    }

    @Test fun invalidCoordinatesAndClockReversalFailClosed() {
        val hold = HoldGesture(500, 8f)
        assertFalse(hold.down(Float.NaN, 20f, 1_000L))
        assertFalse(hold.down(10f, 20f, -1L))
        hold.down(10f, 20f, 1_000L)
        assertFalse(hold.move(Float.POSITIVE_INFINITY, 20f))
        hold.down(10f, 20f, 1_000L)
        assertNull(hold.poll(999L))
        assertNull(hold.poll(1_500L))
        assertThrows(IllegalArgumentException::class.java) { HoldGesture(249, 8f) }
        assertThrows(IllegalArgumentException::class.java) { HoldGesture(501, 8f) }
        assertThrows(IllegalArgumentException::class.java) { HoldGesture(350, Float.NaN) }
    }

    @Test fun movementAfterEmissionStillCancelsAnUnsentRequest() {
        val hold = HoldGesture(350, 8f)
        hold.down(10f, 20f, 1_000L)
        assertNotNull(hold.poll(1_350L))
        assertTrue(hold.isActive)
        assertFalse(hold.move(50f, 20f))
        assertFalse(hold.isActive)
    }
}
