package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.BackedUpContactDataRow
import io.github.hddq.restoid.model.BackedUpContactGroup
import io.github.hddq.restoid.model.BackedUpRawContact
import io.github.hddq.restoid.model.ContactsBackupArchive
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.buildContactAccountSelectionKey
import io.github.hddq.restoid.model.buildContactDedupKeys
import io.github.hddq.restoid.model.coalesceContactsForImport
import io.github.hddq.restoid.model.normalizePhoneForDedup
import io.github.hddq.restoid.model.selectExportedContactGroups
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ContactsSerializationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    fun `normalizePhoneForDedup preserves country codes and equates local forms only when country is known`() {
        val usIntl = normalizePhoneForDedup("+1 207 946 0958")
        val ukIntl = normalizePhoneForDedup("+44 20 7946 0958")
        assertEquals("+12079460958", usIntl)
        assertEquals("+442079460958", ukIntl)
        assertNotEquals(usIntl, ukIntl)

        assertEquals("+442079460958", normalizePhoneForDedup("0044 20 7946 0958"))

        // Known country equates local and international forms
        assertEquals("+15551234567", normalizePhoneForDedup("+1 (555) 123-4567", "US"))
        assertEquals("+15551234567", normalizePhoneForDedup("555-123-4567", "US"))
        assertEquals("+442079460958", normalizePhoneForDedup("020 7946 0958", "GB"))

        // Unknown country does not equate local number to international number
        assertEquals("5551234567", normalizePhoneForDedup("555-123-4567", null))
        assertNotEquals(
            normalizePhoneForDedup("+1 (555) 123-4567", null),
            normalizePhoneForDedup("555-123-4567", null)
        )
        assertEquals("12345", normalizePhoneForDedup("123-45"))
        assertEquals("", normalizePhoneForDedup("   "))

        val keysWithPhoneAndEmail = buildContactDedupKeys(
            displayName = "Alice Example",
            phones = listOf("+1 (555) 123-4567"),
            emails = listOf("Alice@Example.com"),
            defaultCountryIso = "US"
        )
        assertEquals(setOf("p:+15551234567", "e:alice@example.com"), keysWithPhoneAndEmail)

        val matchingFormattedKeys = buildContactDedupKeys(
            displayName = "A. Example",
            phones = listOf("5551234567"),
            emails = emptyList(),
            defaultCountryIso = "US"
        )
        assertTrue(matchingFormattedKeys.any { it in keysWithPhoneAndEmail })

        val nameFallbackKeys = buildContactDedupKeys(
            displayName = "  Bob Without Details  ",
            phones = listOf(""),
            emails = emptyList()
        )
        assertEquals(setOf("n:bob without details"), nameFallbackKeys)
    }

    @Test
    fun `coalesceContactsForImport combines linked raw contacts without dropping emails, groups, or photos`() {
        val googleRaw = BackedUpRawContact(
            accountName = "alice@gmail.com",
            accountType = "com.google",
            displayName = "Alice Example",
            photoFileName = null,
            groupTitles = listOf("Work"),
            dataRows = listOf(
                BackedUpContactDataRow(
                    mimeType = "vnd.android.cursor.item/name",
                    data = mapOf("data1" to "Alice Example", "data2" to "Alice")
                ),
                BackedUpContactDataRow(
                    mimeType = "vnd.android.cursor.item/phone_v2",
                    isPrimary = true,
                    data = mapOf("data1" to "+1 (207) 946-0958", "data2" to "2")
                )
            )
        )
        val localRaw = BackedUpRawContact(
            starred = true,
            sendToVoicemail = true,
            displayName = "Alice Example",
            photoFileName = "contact_2.jpg",
            groupTitles = listOf("Family"),
            dataRows = listOf(
                BackedUpContactDataRow(
                    mimeType = "vnd.android.cursor.item/phone_v2",
                    data = mapOf("data1" to "207-946-0958", "data2" to "2")
                ),
                BackedUpContactDataRow(
                    mimeType = "vnd.android.cursor.item/email_v2",
                    data = mapOf("data1" to "alice@example.com", "data2" to "1")
                )
            )
        )
        val distinctUkContact = BackedUpRawContact(
            displayName = "Bob UK",
            dataRows = listOf(
                BackedUpContactDataRow(
                    mimeType = "vnd.android.cursor.item/phone_v2",
                    data = mapOf("data1" to "+44 20 7946 0958", "data2" to "2")
                )
            )
        )

        val coalesced = coalesceContactsForImport(
            contacts = listOf(googleRaw, localRaw, distinctUkContact),
            defaultCountryIso = "US"
        )

        assertEquals(2, coalesced.size)
        val mergedAlice = coalesced[0]
        assertTrue(mergedAlice.starred)
        assertTrue(mergedAlice.sendToVoicemail)
        assertEquals("contact_2.jpg", mergedAlice.photoFileName)
        assertEquals(listOf("Work", "Family"), mergedAlice.groupTitles)
        assertEquals(3, mergedAlice.dataRows.size)
        assertTrue(
            mergedAlice.dataRows.any {
                it.mimeType == "vnd.android.cursor.item/email_v2" && it.data["data1"] == "alice@example.com"
            }
        )
        assertEquals("Bob UK", coalesced[1].displayName)
    }

    @Test
    fun `resolveSafePhotoFile rejects traversal, absolute paths, and symlinks outside photosDir`() {
        val root = tempFolder.newFolder("backup")
        val photosDir = File(root, "photos").apply { mkdirs() }
        val validPhoto = File(photosDir, "contact_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val outsideSecret = File(root, "secret.jpg").apply { writeBytes(byteArrayOf(9, 9, 9)) }

        assertEquals(validPhoto.canonicalFile, resolveSafePhotoFile(photosDir, "contact_1.jpg")?.canonicalFile)
        assertNull(resolveSafePhotoFile(photosDir, "../secret.jpg"))
        assertNull(resolveSafePhotoFile(photosDir, outsideSecret.absolutePath))
        assertFalse(validPhoto.name.contains(".."))

        val symlink = photosDir.toPath().resolve("link.jpg")
        Files.createSymbolicLink(symlink, outsideSecret.toPath())
        assertNull(resolveSafePhotoFile(photosDir, "link.jpg"))

        val externalTargetDir = tempFolder.newFolder("external_photos_target")
        val targetSecret = File(externalTargetDir, "secret.jpg").apply { writeBytes(byteArrayOf(7, 7, 7)) }
        assertTrue(targetSecret.exists())

        val linkedPhotosDir = File(tempFolder.newFolder("linked_photos_backup"), "photos")
        Files.createSymbolicLink(linkedPhotosDir.toPath(), externalTargetDir.toPath())
        assertNull(resolveSafePhotoFile(linkedPhotosDir, "secret.jpg"))

        val realTargetContactsDir = tempFolder.newFolder("real_contacts_target").apply {
            File(this, "photos").apply {
                mkdirs()
                File(this, "secret.jpg").writeBytes(byteArrayOf(5, 5, 5))
            }
        }
        val linkedContactsDir = File(root, "linked_contacts")
        Files.createSymbolicLink(linkedContactsDir.toPath(), realTargetContactsDir.toPath())
        assertNull(resolveSafePhotoFile(File(linkedContactsDir, "photos"), "secret.jpg"))
    }

    @Test
    fun `buildContactAccountSelectionKey preserves raw account fields and groups null and empty local accounts`() {
        val googleKey = buildContactAccountSelectionKey(
            accountType = "com.google",
            accountName = "user@example.com"
        )
        assertEquals("com.google\u001Fuser@example.com", googleKey)

        val paddedNameKey = buildContactAccountSelectionKey(
            accountType = "com.google",
            accountName = " team "
        )
        assertEquals("com.google\u001F team ", paddedNameKey)
        assertNotEquals(
            paddedNameKey,
            buildContactAccountSelectionKey(accountType = "com.google", accountName = "team")
        )

        val localNullKey = buildContactAccountSelectionKey(accountType = null, accountName = null)
        val localEmptyKey = buildContactAccountSelectionKey(accountType = "", accountName = "")
        val whitespaceOnlyKey = buildContactAccountSelectionKey(accountType = "  ", accountName = "")
        assertEquals("\u001F", localNullKey)
        assertEquals(localNullKey, localEmptyKey)
        assertNotEquals(localNullKey, whitespaceOnlyKey)
    }

    @Test
    fun `selectExportedContactGroups filters excluded accounts before title deduplication`() {
        val excludedGroup = BackedUpContactGroup(
            title = "Work",
            notes = "Excluded account notes",
            accountName = "excluded@example.com",
            accountType = "com.work"
        )
        val selectedGroup = BackedUpContactGroup(
            title = "Work",
            notes = "Selected account notes",
            accountName = "selected@example.com",
            accountType = "com.google"
        )
        val unusedGroup = BackedUpContactGroup(
            title = "Unused",
            notes = "Not referenced",
            accountName = "selected@example.com",
            accountType = "com.google"
        )
        val selectedKey = buildContactAccountSelectionKey("com.google", "selected@example.com")

        val restrictedGroups = selectExportedContactGroups(
            groups = listOf(excludedGroup, selectedGroup, unusedGroup),
            referencedGroupTitles = setOf("Work"),
            selectAllContactAccounts = false,
            selectedContactAccountKeys = setOf(selectedKey)
        )
        assertEquals(listOf(selectedGroup), restrictedGroups)

        val allAccountGroups = selectExportedContactGroups(
            groups = listOf(excludedGroup, selectedGroup, unusedGroup),
            referencedGroupTitles = setOf("Work"),
            selectAllContactAccounts = true,
            selectedContactAccountKeys = emptySet()
        )
        assertEquals(listOf(excludedGroup), allAccountGroups)
    }

    @Test
    fun `DeviceDataBackupSelection round-trips contact account selection and ignores legacy includeReadOnlyContacts`() {
        val defaultSelection = DeviceDataBackupSelection()
        assertTrue(defaultSelection.selectAllContactAccounts)
        assertTrue(defaultSelection.selectedContactAccountKeys.isEmpty())

        val selectedKey = buildContactAccountSelectionKey("com.google", "user@example.com")
        val customSelection = DeviceDataBackupSelection(
            contacts = true,
            selectAllContactAccounts = false,
            selectedContactAccountKeys = setOf(selectedKey, "\u001F")
        )
        val encoded = json.encodeToString(customSelection)
        val decoded = json.decodeFromString<DeviceDataBackupSelection>(encoded)
        assertEquals(customSelection, decoded)

        val legacySelection = json.decodeFromString<DeviceDataBackupSelection>(
            """{"contacts":true,"includeReadOnlyContacts":true}"""
        )
        assertTrue(legacySelection.contacts)
        assertTrue(legacySelection.selectAllContactAccounts)
        assertTrue(legacySelection.selectedContactAccountKeys.isEmpty())
    }
}
