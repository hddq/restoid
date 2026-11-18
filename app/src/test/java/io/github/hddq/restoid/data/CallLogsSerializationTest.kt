package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.BackedUpCallLogEntry
import io.github.hddq.restoid.model.CallLogsBackupArchive
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.buildCallLogDedupKey
import io.github.hddq.restoid.model.toDedupKey
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import io.github.hddq.restoid.work.DeviceDataRestoreSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogsSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `CallLogsBackupArchive round-trips call log entries and optional fields`() {
        val archive = CallLogsBackupArchive(
            version = 1,
            calls = listOf(
                BackedUpCallLogEntry(
                    number = "+1 (555) 123-4567",
                    presentation = 1,
                    type = 2,
                    date = 1_700_000_000_000L,
                    duration = 95L,
                    isNew = 0,
                    cachedName = "Alice Example",
                    cachedNumberType = 2,
                    cachedNumberLabel = "Mobile",
                    countryIso = "US",
                    geocodedLocation = "California",
                    features = 1,
                    isRead = 1,
                    phoneAccountComponentName = "com.android.phone/.TelephonyConnectionService",
                    phoneAccountId = "sub1",
                    voicemailUri = null,
                    postDialDigits = ";123",
                    viaNumber = null,
                    callScreeningAppName = "Dialer",
                    callScreeningComponentName = "com.google.android.dialer/.Screening",
                    blockReason = 0
                ),
                BackedUpCallLogEntry(
                    number = null,
                    presentation = 2,
                    type = 3,
                    date = 1_700_000_500_000L,
                    duration = 0L
                )
            )
        )

        val encoded = json.encodeToString(archive)
        val decoded = json.decodeFromString<CallLogsBackupArchive>(encoded)

        assertEquals(archive, decoded)
    }

    @Test
    fun `RestoidMetadata deserializes legacy deviceData without callLogs and round-trips with callLogs`() {
        val legacyWithContactsOnlyJson = """
            {
              "apps": {
                "com.example.app": {
                  "size": 1024,
                  "types": ["apk"],
                  "versionCode": 1,
                  "versionName": "1.0"
                }
              },
              "customDirectories": {},
              "deviceData": {
                "contacts": {
                  "count": 42,
                  "size": 16384,
                  "path": "/data/user/0/io.github.hddq.restoid/files/device_data_staging/contacts"
                }
              }
            }
        """.trimIndent()

        val legacyDecoded = json.decodeFromString<RestoidMetadata>(legacyWithContactsOnlyJson)
        assertNull(legacyDecoded.deviceData?.callLogs)
        assertEquals(42, legacyDecoded.deviceData?.contacts?.count)

        val withCallLogs = RestoidMetadata(
            apps = mapOf(
                "com.example.app" to AppMetadata(
                    size = 1024L,
                    types = listOf("apk"),
                    versionCode = 1L,
                    versionName = "1.0"
                )
            ),
            customDirectories = emptyMap(),
            deviceData = DeviceDataMetadata(
                contacts = ContactsBackupMetadata(
                    count = 42,
                    size = 16_384L,
                    path = "/staging/contacts"
                ),
                callLogs = CallLogsBackupMetadata(
                    count = 150,
                    size = 32_768L,
                    path = "/staging/call_logs"
                )
            )
        )

        val encoded = json.encodeToString(withCallLogs)
        val decoded = json.decodeFromString<RestoidMetadata>(encoded)
        assertEquals(withCallLogs, decoded)
    }

    @Test
    fun `buildCallLogDedupKey normalizes phone numbers and distinguishes timestamp type duration and presentation`() {
        val entry = BackedUpCallLogEntry(
            number = "+1 (555) 123-4567",
            presentation = 1,
            type = 2,
            date = 1_700_000_000_000L,
            duration = 45L,
            countryIso = "US"
        )
        val localKey = buildCallLogDedupKey(
            number = "555-123-4567",
            presentation = 1,
            type = 2,
            date = 1_700_000_000_000L,
            duration = 45L,
            defaultCountryIso = "US"
        )
        assertEquals(entry.toDedupKey(), localKey)

        // Different duration, type, or date produce distinct keys
        assertNotEquals(
            entry.toDedupKey(),
            entry.copy(duration = 46L).toDedupKey()
        )
        assertNotEquals(
            entry.toDedupKey(),
            entry.copy(type = 1).toDedupKey()
        )
        assertNotEquals(
            entry.toDedupKey(),
            entry.copy(date = 1_700_000_000_001L).toDedupKey()
        )

        // Empty/hidden numbers key by presentation
        val privateKey = buildCallLogDedupKey(
            number = "",
            presentation = 2,
            type = 3,
            date = 1_700_000_000_000L,
            duration = 0L
        )
        val payphoneKey = buildCallLogDedupKey(
            number = null,
            presentation = 4,
            type = 3,
            date = 1_700_000_000_000L,
            duration = 0L
        )
        assertEquals("p:2|1700000000000|3|0", privateKey)
        assertEquals(
            privateKey,
            buildCallLogDedupKey("Private", 2, 3, 1_700_000_000_000L, 0L)
        )
        assertEquals(
            privateKey,
            buildCallLogDedupKey("-2", 2, 3, 1_700_000_000_000L, 0L)
        )
        assertEquals(
            "p:3|1700000000000|3|0",
            buildCallLogDedupKey("Unknown", 3, 3, 1_700_000_000_000L, 0L)
        )
        assertEquals(
            "p:5|1700000000000|3|0",
            buildCallLogDedupKey("Unavailable", 5, 3, 1_700_000_000_000L, 0L)
        )
        assertNotEquals(privateKey, payphoneKey)

        // Alphanumeric caller IDs with allowed presentation normalize case-insensitively
        assertEquals(
            buildCallLogDedupKey("Voicemail", 1, 4, 1000L, 12L),
            buildCallLogDedupKey("voicemail", 1, 4, 1000L, 12L)
        )
    }

    @Test
    fun `DeviceData selection defaults leave callLogs disabled and deserialize legacy payloads`() {
        val defaultBackup = DeviceDataBackupSelection()
        assertFalse(defaultBackup.contacts)
        assertFalse(defaultBackup.callLogs)
        assertFalse(defaultBackup.anyEnabled())
        assertTrue(DeviceDataBackupSelection(callLogs = true).anyEnabled())

        val defaultRestore = DeviceDataRestoreSelection()
        assertFalse(defaultRestore.contacts)
        assertFalse(defaultRestore.callLogs)
        assertFalse(defaultRestore.anyEnabled())
        assertTrue(DeviceDataRestoreSelection(callLogs = true).anyEnabled())

        val legacyBackupSelection = json.decodeFromString<DeviceDataBackupSelection>("""{"contacts":true}""")
        assertTrue(legacyBackupSelection.contacts)
        assertFalse(legacyBackupSelection.callLogs)

        val legacyRestoreSelection = json.decodeFromString<DeviceDataRestoreSelection>("""{"contacts":true}""")
        assertTrue(legacyRestoreSelection.contacts)
        assertFalse(legacyRestoreSelection.callLogs)
    }
}
