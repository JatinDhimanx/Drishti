package org.drishti.core.location

import org.drishti.core.model.LocationSharingInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FamilySafetyDispatcherTest {

    private lateinit var dispatcher: FamilySafetyDispatcher

    @Before
    fun setup() {
        dispatcher = FamilySafetyDispatcher(userLanguage = "en")
    }

    @Test
    fun testInitialDispatchAlwaysAllowed() {
        // First dispatch when lastDispatchTimestampMs == 0 should always be true
        assertTrue(dispatcher.shouldDispatch(LocationSharingInterval.MINUTES_5, 1000L))
    }

    @Test
    fun testIntervalGatingEnforced() {
        val t0 = 100000L
        dispatcher.recordSuccessfulDispatch(t0)

        // 2 minutes later for a 5-minute interval -> should NOT dispatch
        val t1 = t0 + (2 * 60 * 1000L)
        assertFalse(dispatcher.shouldDispatch(LocationSharingInterval.MINUTES_5, t1))

        // Exactly 5 minutes later -> should dispatch!
        val t2 = t0 + (5 * 60 * 1000L)
        assertTrue(dispatcher.shouldDispatch(LocationSharingInterval.MINUTES_5, t2))
    }

    @Test
    fun testSosMessageFormatting() {
        val msg = dispatcher.formatBroadcastMessage(
            latitude = 28.6129,
            longitude = 77.2295,
            state = FamilySafetyDispatcher.UserSafetyState.SOS_TRIGGERED
        )

        assertTrue(msg.startsWith("EMERGENCY SOS:"))
        assertTrue(msg.contains("https://maps.google.com/?q=28.6129,77.2295"))
    }

    @Test
    fun testRouteDeviatedMessageFormatting() {
        val msg = dispatcher.formatBroadcastMessage(
            latitude = 28.6129,
            longitude = 77.2295,
            state = FamilySafetyDispatcher.UserSafetyState.ROUTE_DEVIATED
        )

        assertTrue(msg.contains("deviated", ignoreCase = true))
        assertTrue(msg.contains("https://maps.google.com/?q=28.6129,77.2295"))
    }

    @Test
    fun testOfflineQueueBufferingAndDrain() {
        assertEquals(0, dispatcher.getPendingQueueSize())

        val ping1 = FamilySafetyDispatcher.QueuedLocationPing(28.6, 77.2, FamilySafetyDispatcher.UserSafetyState.WALKING, 1000L)
        val ping2 = FamilySafetyDispatcher.QueuedLocationPing(28.601, 77.201, FamilySafetyDispatcher.UserSafetyState.WALKING, 2000L)

        dispatcher.enqueueOfflinePing(ping1)
        dispatcher.enqueueOfflinePing(ping2)

        assertEquals(2, dispatcher.getPendingQueueSize())

        val drained = dispatcher.drainOfflineQueue()
        assertEquals(2, drained.size)
        assertEquals(0, dispatcher.getPendingQueueSize()) // Queue should be empty after drain
    }
}

