package com.chakshu

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chakshu.core.db.ChakshuDatabase
import com.chakshu.core.db.daos.ChunkDao
import com.chakshu.core.db.daos.ContactDao
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.daos.InterferenceEventDao
import com.chakshu.core.db.entities.ChunkEntity
import com.chakshu.core.db.entities.ContactEntity
import com.chakshu.core.db.entities.IncidentEntity
import com.chakshu.core.db.entities.InterferenceEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: ChakshuDatabase
    private lateinit var incidentDao: IncidentDao
    private lateinit var chunkDao: ChunkDao
    private lateinit var contactDao: ContactDao
    private lateinit var interferenceEventDao: InterferenceEventDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChakshuDatabase::class.java
        ).allowMainThreadQueries().build()
        incidentDao = db.incidentDao()
        chunkDao = db.chunkDao()
        contactDao = db.contactDao()
        interferenceEventDao = db.interferenceEventDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // IncidentDao

    @Test
    fun incidentDao_insertAndGetById() = runTest {
        val incident = testIncident("inc-1")
        incidentDao.insert(incident)
        val loaded = incidentDao.getById("inc-1")
        assertNotNull(loaded)
        assertEquals("ACTIVE", loaded!!.status)
    }

    @Test
    fun incidentDao_updateStatus() = runTest {
        incidentDao.insert(testIncident("inc-2"))
        incidentDao.updateStatus("inc-2", "ENDED")
        assertEquals("ENDED", incidentDao.getById("inc-2")!!.status)
    }

    @Test
    fun incidentDao_getByStatus() = runTest {
        incidentDao.insert(testIncident("inc-3"))
        incidentDao.insert(testIncident("inc-4").copy(status = "ENDED"))
        assertEquals(1, incidentDao.getByStatus("ACTIVE").size)
        assertEquals(1, incidentDao.getByStatus("ENDED").size)
    }

    // ChunkDao

    @Test
    fun chunkDao_insertAndGetById() = runTest {
        incidentDao.insert(testIncident("inc-c1"))
        val chunk = testChunk("chunk-1", "inc-c1")
        chunkDao.insert(chunk)
        val loaded = chunkDao.getById("chunk-1")
        assertNotNull(loaded)
        assertEquals("PENDING", loaded!!.status)
    }

    @Test
    fun chunkDao_getByIncidentId() = runTest {
        incidentDao.insert(testIncident("inc-c2"))
        chunkDao.insert(testChunk("chunk-2a", "inc-c2"))
        chunkDao.insert(testChunk("chunk-2b", "inc-c2").copy(chunkIndex = 1))
        assertEquals(2, chunkDao.getByIncidentId("inc-c2").size)
    }

    @Test
    fun chunkDao_getByStatus() = runTest {
        incidentDao.insert(testIncident("inc-c3"))
        chunkDao.insert(testChunk("chunk-3a", "inc-c3"))
        chunkDao.insert(testChunk("chunk-3b", "inc-c3").copy(chunkIndex = 1, status = "DONE"))
        assertEquals(1, chunkDao.getByStatus("PENDING").size)
        assertEquals(1, chunkDao.getByStatus("DONE").size)
    }

    // ContactDao

    @Test
    fun contactDao_insertAndGetById() = runTest {
        contactDao.insert(testContact("con-1"))
        val loaded = contactDao.getById("con-1")
        assertNotNull(loaded)
        assertEquals("Test User", loaded!!.name)
    }

    @Test
    fun contactDao_getAll() = runTest {
        contactDao.insert(testContact("con-2"))
        contactDao.insert(testContact("con-3").copy(name = "Other"))
        assertEquals(2, contactDao.getAll().size)
    }

    @Test
    fun contactDao_delete() = runTest {
        val c = testContact("con-4")
        contactDao.insert(c)
        contactDao.delete(c)
        assertNull(contactDao.getById("con-4"))
    }

    @Test
    fun contactDao_getAllSmsEnabled() = runTest {
        contactDao.insert(testContact("con-5"))
        contactDao.insert(testContact("con-6").copy(notifySms = false))
        assertEquals(1, contactDao.getAllSmsEnabled().size)
    }

    // InterferenceEventDao

    @Test
    fun interferenceEventDao_insertAndGetById() = runTest {
        incidentDao.insert(testIncident("inc-ie1"))
        val event = testInterferenceEvent("ie-1", "inc-ie1")
        interferenceEventDao.insert(event)
        assertNotNull(interferenceEventDao.getById("ie-1"))
    }

    @Test
    fun interferenceEventDao_getByIncidentId() = runTest {
        incidentDao.insert(testIncident("inc-ie2"))
        interferenceEventDao.insert(testInterferenceEvent("ie-2a", "inc-ie2"))
        interferenceEventDao.insert(testInterferenceEvent("ie-2b", "inc-ie2"))
        assertEquals(2, interferenceEventDao.getByIncidentId("inc-ie2").size)
    }

    @Test
    fun interferenceEventDao_getUnsynced() = runTest {
        incidentDao.insert(testIncident("inc-ie3"))
        interferenceEventDao.insert(testInterferenceEvent("ie-3a", "inc-ie3"))
        interferenceEventDao.insert(testInterferenceEvent("ie-3b", "inc-ie3").copy(synced = true))
        assertEquals(1, interferenceEventDao.getUnsynced().size)
    }

    // Helpers

    private fun testIncident(id: String) = IncidentEntity(
        id = id, triggeredAt = 0L, deviceHash = "hash", batteryPct = 80,
        networkType = "WIFI", cellTowerId = null, lat = null, lon = null,
        status = "ACTIVE", createdAt = 0L
    )

    private fun testChunk(id: String, incidentId: String) = ChunkEntity(
        id = id, incidentId = incidentId, chunkIndex = 0, filePath = "/tmp/$id.aac",
        durationMs = 30_000L, sha256 = null, ipfsCid = null, otsProof = null,
        lat = null, lon = null, recordedAt = 0L, uploadedAt = null, status = "PENDING"
    )

    private fun testContact(id: String) = ContactEntity(
        id = id, name = "Test User", phone = "+911234567890",
        notifySms = true, notifyPush = false, addedAt = 0L
    )

    private fun testInterferenceEvent(id: String, incidentId: String) = InterferenceEventEntity(
        id = id, incidentId = incidentId, eventType = "ADB_CONNECTED",
        detectedAt = 0L, synced = false
    )
}
