package org.drishti.core.database

import org.drishti.core.model.LocationSharingInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DestinationAndSafetyRepositoryTest {

    private lateinit var destRepo: DestinationRepository
    private lateinit var safetyRepo: FamilySafetyRepository

    @Before
    fun setUp() {
        destRepo = DestinationRepository()
        safetyRepo = FamilySafetyRepository()
    }

    @Test
    fun testAddAndRemoveDestination() {
        val initialSize = destRepo.destinations.value.size
        val added = destRepo.addDestination("Test Metro Station", 28.6200, 77.2100)

        assertEquals("Test Metro Station", added.label)
        assertEquals(initialSize + 1, destRepo.destinations.value.size)

        destRepo.removeDestination(added.id)
        assertEquals(initialSize, destRepo.destinations.value.size)
    }

    @Test
    fun testFamilyContactManagement() {
        val initialCount = safetyRepo.contacts.value.size
        val newContact = safetyRepo.addContact("Sister", "+919899001122", isPrimary = false)

        assertEquals(initialCount + 1, safetyRepo.contacts.value.size)
        assertFalse(newContact.isPrimaryEmergencyContact)

        safetyRepo.setPrimaryContact(newContact.id)
        val updated = safetyRepo.contacts.value.find { it.id == newContact.id }
        assertNotNull(updated)
        assertTrue(updated!!.isPrimaryEmergencyContact)

        safetyRepo.removeContact(newContact.id)
        assertEquals(initialCount, safetyRepo.contacts.value.size)
    }

    @Test
    fun testLiveLocationSharingToggleAndInterval() {
        assertFalse(safetyRepo.sharingStatus.value.isLiveSharingActive)

        safetyRepo.setLiveSharingActive(true)
        assertTrue(safetyRepo.sharingStatus.value.isLiveSharingActive)

        safetyRepo.updateSharingInterval(LocationSharingInterval.MINUTES_15)
        assertEquals(LocationSharingInterval.MINUTES_15, safetyRepo.sharingStatus.value.interval)

        val sms = safetyRepo.formatSmsAlert(28.6139, 77.2090, isEmergency = true)
        assertTrue("SMS should contain emergency SOS text", sms.contains("EMERGENCY SOS"))
        assertTrue("SMS should contain maps link", sms.contains("https://maps.google.com/?q=28.6139,77.209"))
    }
}

