package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.BackedUpContactDataRow
import io.github.hddq.restoid.model.BackedUpContactGroup
import io.github.hddq.restoid.model.BackedUpRawContact
import io.github.hddq.restoid.model.ContactsBackupArchive
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.buildContactDedupKeys
import io.github.hddq.restoid.model.normalizePhoneForDedup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `ContactsBackupArchive round-trips groups, raw contacts, primary flags, and photos`() {
        val archive = ContactsBackupArchive(
            version = 1,
            groups = listOf(
                BackedUpContactGroup(
                    title = "Family",
                    notes = "Close family",
                    accountName = "user@example.com",
                    accountType = "com.google"
                )
            ),
            contacts = listOf(
                BackedUpRawContact(
                    accountName = "user@example.com",
                    accountType = "com.google",
                    readOnly = false,
                    starred = true,
                    customRingtone = "content://media/internal/audio/media/42",
                    sendToVoicemail = false,
                    displayName = "Alice Example",
                    photoFileName = "contact_0.jpg",
                    groupTitles = listOf("Family"),
                    dataRows = listOf(
                        BackedUpContactDataRow(
                            mimeType = "vnd.android.cursor.item/phone_v2",
                            isPrimary = true,
                            isSuperPrimary = true,
                            data = mapOf("data1" to "+1 (555) 123-4567", "data2" to "2")
                        ),
                        BackedUpContactDataRow(
                            mimeType = "vnd.android.cursor.item/email_v2",
                            isPrimary = false,
                            isSuperPrimary = false,
                            data = mapOf("data1" to "Alice@Example.com", "data2" to "1")
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(archive)
        val decoded = json.decodeFromString<ContactsBackupArchive>(encoded)

        assertEquals(archive, decoded)
    }

    @Test
    fun `RestoidMetadata deserializes legacy snapshots without deviceData and round-trips with contacts`() {
        val legacyJson = """
            {
              "apps": {
                "com.example.app": {
                  "size": 1024,
                  "types": ["apk"],
                  "versionCode": 1,
                  "versionName": "1.0"
                }
              },
              "customDirectories": {}
            }
        """.trimIndent()

        val legacyDecoded = json.decodeFromString<RestoidMetadata>(legacyJson)
        assertNull(legacyDecoded.deviceData)

        val withDeviceData = RestoidMetadata(
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
                    path = "/data/user/0/io.github.hddq.restoid/files/device_data_staging/contacts"
                )
            )
        )

        val encoded = json.encodeToString(withDeviceData)
        val decoded = json.decodeFromString<RestoidMetadata>(encoded)
        assertEquals(withDeviceData, decoded)
    }

    @Test
    fun `normalizePhoneForDedup and buildContactDedupKeys match equivalent formats and fall back to name`() {
        assertEquals("5551234567", normalizePhoneForDedup("+1 (555) 123-4567"))
        assertEquals("5551234567", normalizePhoneForDedup("555-123-4567"))
        assertEquals("12345", normalizePhoneForDedup("123-45"))
        assertEquals("", normalizePhoneForDedup("   "))

        val keysWithPhoneAndEmail = buildContactDedupKeys(
            displayName = "Alice Example",
            phones = listOf("+1 (555) 123-4567"),
            emails = listOf("Alice@Example.com")
        )
        assertEquals(setOf("p:5551234567", "e:alice@example.com"), keysWithPhoneAndEmail)

        val matchingFormattedKeys = buildContactDedupKeys(
            displayName = "A. Example",
            phones = listOf("5551234567"),
            emails = emptyList()
        )
        assertTrue(matchingFormattedKeys.any { it in keysWithPhoneAndEmail })

        val nameFallbackKeys = buildContactDedupKeys(
            displayName = "  Bob Without Details  ",
            phones = listOf(""),
            emails = emptyList()
        )
        assertEquals(setOf("n:bob without details"), nameFallbackKeys)
    }
}
