package org.drishti.core.location

import org.drishti.core.model.FamilyContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmergencySosControllerTest {

    private lateinit var controller: EmergencySosController

    @Before
    fun setup() {
        controller = EmergencySosController(userLanguage = "en")
    }

    @Test
    fun testStartSosCountdown() {
        val status = controller.startSosCountdown(28.6129, 77.2295)

        assertEquals(EmergencySosController.SosState.COUNTDOWN_ACTIVE, status.state)
        assertEquals(3, status.remainingSeconds)
        assertTrue(controller.isSosActive())
        assertTrue(status.spokenConfirmation.contains("3 seconds"))
    }

    @Test
    fun testCancelSosBeforeDispatch() {
        controller.startSosCountdown(28.6129, 77.2295)
        val cancelStatus = controller.cancelSos()

        assertEquals(EmergencySosController.SosState.CANCELLED, cancelStatus.state)
        assertFalse(controller.isSosActive())
        assertTrue(cancelStatus.spokenConfirmation.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun testExecuteSosDispatchToContacts() {
        val contacts = listOf(
            FamilyContact(1L, "Mom", "+919999999991", isLocationSharingEnabled = true),
            FamilyContact(2L, "Dad", "+919999999992", isLocationSharingEnabled = true),
            FamilyContact(3L, "Doctor", "+919999999993", isLocationSharingEnabled = false) // Disabled
        )

        controller.startSosCountdown(28.6129, 77.2295)
        val dispatchStatus = controller.executeSosDispatch(contacts)

        assertEquals(EmergencySosController.SosState.SOS_DISPATCHED, dispatchStatus.state)
        assertEquals(2, dispatchStatus.dispatchedContactsCount) // Only 2 enabled contacts
        assertTrue(dispatchStatus.spokenConfirmation.contains("2 emergency contacts"))
    }

    @Test
    fun testFormatSafeStandDownMessage() {
        val msg = controller.formatSafeStandDownMessage(28.6129, 77.2295)

        assertTrue(msg.contains("now safe", ignoreCase = true))
        assertTrue(msg.contains("https://maps.google.com/?q=28.6129,77.2295"))
    }
}

