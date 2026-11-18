package io.github.hddq.restoid.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.model.BackedUpContactDataRow
import io.github.hddq.restoid.model.BackedUpContactGroup
import io.github.hddq.restoid.model.BackedUpRawContact
import io.github.hddq.restoid.model.ContactsBackupArchive
import io.github.hddq.restoid.model.buildContactDedupKeys
import io.github.hddq.restoid.model.coalesceContactsForImport
import io.github.hddq.restoid.model.extractDedupKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

data class ExportedContactsResult(
    val contactCount: Int,
    val totalSizeBytes: Long,
    val outputDir: File
)

data class ImportedContactsResult(
    val restoredCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val failedGroupCount: Int = 0,
    val failedPhotoCount: Int = 0
)

private data class EnsureLocalGroupsResult(
    val titleToId: Map<String, Long>,
    val failedTitles: Set<String>
)

internal fun resolveSafePhotoFile(photosDir: File, photoFileName: String): File? {
    if (photoFileName.isBlank()) return null
    val photosPath = photosDir.toPath().normalize()
    if (!Files.isDirectory(photosPath, LinkOption.NOFOLLOW_LINKS)) return null
    if (photosPath.parent?.let { Files.isSymbolicLink(it) } == true) return null
    val photosRealPath = runCatching { photosPath.toRealPath() }.getOrNull() ?: return null
    val candidatePath = runCatching { photosRealPath.resolve(photoFileName).normalize() }.getOrNull() ?: return null
    if (!candidatePath.startsWith(photosRealPath) || candidatePath == photosRealPath) return null

    var current: Path? = candidatePath
    while (current != null && current != photosRealPath) {
        if (Files.isSymbolicLink(current)) return null
        current = current.parent
    }

    val realCandidate = runCatching { candidatePath.toRealPath() }.getOrNull() ?: return null
    if (!realCandidate.startsWith(photosRealPath) || realCandidate == photosRealPath) return null
    if (!Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS)) return null
    return realCandidate.toFile()
}

class ContactsBackupRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasWriteContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun exportContacts(
        outputDir: File,
        includeReadOnly: Boolean,
        shouldStop: () -> Boolean = { false }
    ): ExportedContactsResult = withContext(Dispatchers.IO) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val resolver = context.contentResolver
        val readOnlyAccountTypes = ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && !it.supportsUploading() }
            .mapTo(hashSetOf()) { it.accountType }

        val groupById = mutableMapOf<Long, BackedUpContactGroup>()
        resolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.NOTES,
                ContactsContract.Groups.ACCOUNT_NAME,
                ContactsContract.Groups.ACCOUNT_TYPE
            ),
            "${ContactsContract.Groups.DELETED} = 0",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext() && !shouldStop()) {
                val title = cursor.getString(1)?.trim().orEmpty()
                if (title.isEmpty()) continue
                groupById[cursor.getLong(0)] = BackedUpContactGroup(
                    title = title,
                    notes = cursor.getString(2),
                    accountName = cursor.getString(3),
                    accountType = cursor.getString(4)
                )
            }
        }

        val rawHeaders = linkedMapOf<Long, BackedUpRawContact>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.STARRED,
                ContactsContract.RawContacts.CUSTOM_RINGTONE,
                ContactsContract.RawContacts.SEND_TO_VOICEMAIL,
                ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
            ),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            "${ContactsContract.RawContacts._ID} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext() && !shouldStop()) {
                val accountType = cursor.getString(2)
                val isReadOnly = accountType != null && accountType in readOnlyAccountTypes
                if (!includeReadOnly && isReadOnly) continue
                rawHeaders[cursor.getLong(0)] = BackedUpRawContact(
                    accountName = cursor.getString(1),
                    accountType = accountType,
                    readOnly = isReadOnly,
                    starred = cursor.getInt(3) == 1,
                    customRingtone = cursor.getString(4),
                    sendToVoicemail = cursor.getInt(5) == 1,
                    displayName = cursor.getString(6)
                )
            }
        }

        val dataRowsByRawId = mutableMapOf<Long, MutableList<BackedUpContactDataRow>>()
        val groupTitlesByRawId = mutableMapOf<Long, MutableSet<String>>()
        val thumbnailsByRawId = mutableMapOf<Long, ByteArray>()

        if (rawHeaders.isNotEmpty() && !shouldStop()) {
            val dataColumns = (1..15).map { "data$it" }.toTypedArray()
            val projection = arrayOf(
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.IS_PRIMARY,
                ContactsContract.Data.IS_SUPER_PRIMARY,
                *dataColumns
            )
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Data._ID} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && !shouldStop()) {
                    val rawId = cursor.getLong(0)
                    if (rawId !in rawHeaders) continue
                    val mimeType = cursor.getString(1) ?: continue

                    when (mimeType) {
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> {
                            val photoCol = 4 + 14 // data15 = Photo.PHOTO
                            if (cursor.getType(photoCol) == Cursor.FIELD_TYPE_BLOB) {
                                cursor.getBlob(photoCol)?.takeIf { it.isNotEmpty() }?.let {
                                    thumbnailsByRawId[rawId] = it
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                            val groupCol = 4 // data1 = GroupMembership.GROUP_ROW_ID
                            if (!cursor.isNull(groupCol)) {
                                groupById[cursor.getLong(groupCol)]?.title?.let { title ->
                                    groupTitlesByRawId.getOrPut(rawId) { linkedSetOf() }.add(title)
                                }
                            }
                        }
                        else -> {
                            val dataMap = sortedMapOf<String, String>()
                            for (i in dataColumns.indices) {
                                val colIdx = 4 + i
                                if (cursor.isNull(colIdx)) continue
                                val value = when (cursor.getType(colIdx)) {
                                    Cursor.FIELD_TYPE_STRING -> cursor.getString(colIdx)
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(colIdx).toString()
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(colIdx).toString()
                                    else -> null
                                }
                                if (value != null) dataMap[dataColumns[i]] = value
                            }
                            if (dataMap.isNotEmpty()) {
                                dataRowsByRawId.getOrPut(rawId) { mutableListOf() }.add(
                                    BackedUpContactDataRow(
                                        mimeType = mimeType,
                                        isPrimary = cursor.getInt(2) == 1,
                                        isSuperPrimary = cursor.getInt(3) == 1,
                                        data = dataMap
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val photosDir = File(outputDir, PHOTOS_DIR_NAME)
        val exportedContacts = rawHeaders.entries.mapNotNull { (rawId, header) ->
            if (shouldStop()) return@mapNotNull null
            val rows = dataRowsByRawId[rawId].orEmpty()
            if (rows.isEmpty() && header.displayName.isNullOrBlank()) return@mapNotNull null

            val photoBytes = readDisplayPhotoBytes(rawId) ?: thumbnailsByRawId[rawId]
            val photoFileName = if (photoBytes != null && photoBytes.isNotEmpty()) {
                photosDir.mkdirs()
                val fileName = "contact_$rawId.jpg"
                File(photosDir, fileName).writeBytes(photoBytes)
                fileName
            } else {
                null
            }

            val resolvedName = header.displayName?.takeIf { it.isNotBlank() }
                ?: rows.firstOrNull { it.mimeType == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE }
                    ?.data?.get(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)

            header.copy(
                displayName = resolvedName,
                photoFileName = photoFileName,
                groupTitles = groupTitlesByRawId[rawId].orEmpty().sorted(),
                dataRows = rows
            )
        }

        val referencedGroupTitles = exportedContacts.flatMapTo(linkedSetOf()) { it.groupTitles }
        val archive = ContactsBackupArchive(
            version = 1,
            groups = groupById.values.filter { it.title in referencedGroupTitles }.distinctBy { it.title },
            contacts = exportedContacts
        )
        File(outputDir, CONTACTS_FILE_NAME).writeText(json.encodeToString(archive))

        val totalSizeBytes = outputDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        ExportedContactsResult(exportedContacts.size, totalSizeBytes, outputDir)
    }

    suspend fun importContacts(
        inputDir: File,
        shouldStop: () -> Boolean = { false },
        onContactProcessed: (processed: Int, total: Int, displayName: String?) -> Unit = { _, _, _ -> }
    ): ImportedContactsResult = withContext(Dispatchers.IO) {
        val archiveFile = File(inputDir, CONTACTS_FILE_NAME)
        require(archiveFile.exists()) { "Contacts backup file not found: ${archiveFile.absolutePath}" }

        val archive = json.decodeFromString<ContactsBackupArchive>(archiveFile.readText())
        val photosDir = File(inputDir, PHOTOS_DIR_NAME)
        val resolver = context.contentResolver
        val deviceCountryIso = resolveDeviceCountryIso()
        val e164Formatter: (String, String) -> String? = { number, country ->
            PhoneNumberUtils.formatNumberToE164(number, country)
        }

        val contactsToImport = coalesceContactsForImport(archive.contacts, deviceCountryIso, e164Formatter)
        val existingKeys = if (hasReadContactsPermission()) {
            loadExistingDedupKeys(deviceCountryIso, e164Formatter).toMutableSet()
        } else {
            mutableSetOf()
        }
        val groupsResult = ensureLocalGroups(archive.groups, shouldStop)
        val failedGroupTitles = groupsResult.failedTitles.toMutableSet()

        var restoredCount = 0
        var skippedCount = 0
        var failedCount = 0
        var failedPhotoCount = 0
        val total = contactsToImport.size

        contactsToImport.forEachIndexed { index, contact ->
            if (shouldStop()) {
                return@withContext ImportedContactsResult(
                    restoredCount = restoredCount,
                    skippedCount = skippedCount,
                    failedCount = failedCount,
                    failedGroupCount = failedGroupTitles.size,
                    failedPhotoCount = failedPhotoCount
                )
            }
            onContactProcessed(index + 1, total, contact.displayName)

            val dedupKeys = contact.extractDedupKeys(deviceCountryIso, e164Formatter)

            if (dedupKeys.isNotEmpty() && dedupKeys.any { it in existingKeys }) {
                skippedCount++
                return@forEachIndexed
            }

            var photoBytes: ByteArray? = null
            var contactPhotoFailed = false
            val photoFileName = contact.photoFileName?.takeIf { it.isNotBlank() }
            if (photoFileName != null) {
                val safePhotoFile = resolveSafePhotoFile(photosDir, photoFileName)
                if (safePhotoFile == null) {
                    android.util.Log.w(TAG, "Rejected or missing contact photo path: $photoFileName")
                    contactPhotoFailed = true
                } else {
                    photoBytes = runCatching { safePhotoFile.readBytes() }.onFailure { e ->
                        android.util.Log.w(TAG, "Failed to read contact photo: $photoFileName", e)
                    }.getOrNull()
                    if (photoBytes == null) {
                        contactPhotoFailed = true
                    }
                }
            }

            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .withValue(ContactsContract.RawContacts.STARRED, if (contact.starred) 1 else 0)
                    .withValue(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (contact.sendToVoicemail) 1 else 0)
                    .apply {
                        if (!contact.customRingtone.isNullOrEmpty()) {
                            withValue(ContactsContract.RawContacts.CUSTOM_RINGTONE, contact.customRingtone)
                        }
                    }
                    .build()
            )

            contact.dataRows.forEach { row ->
                val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, row.mimeType)
                    .withValue(ContactsContract.Data.IS_PRIMARY, if (row.isPrimary) 1 else 0)
                    .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, if (row.isSuperPrimary) 1 else 0)
                row.data.forEach { (col, value) -> builder.withValue(col, value) }
                ops.add(builder.build())
            }

            contact.groupTitles.forEach { rawTitle ->
                val title = rawTitle.trim()
                if (title.isEmpty()) return@forEach
                val groupId = groupsResult.titleToId[title]
                if (groupId == null) {
                    android.util.Log.w(TAG, "Missing local group ID for '$title' on contact ${contact.displayName}")
                    failedGroupTitles.add(title)
                    return@forEach
                }
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                        .build()
                )
            }

            if (photoBytes != null && photoBytes.size <= INLINE_PHOTO_LIMIT_BYTES) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }

            try {
                val insertedRawUri = resolver.applyBatch(ContactsContract.AUTHORITY, ops).firstOrNull()?.uri
                if (insertedRawUri == null) {
                    android.util.Log.w(TAG, "Contact insert returned no URI for ${contact.displayName}")
                    failedCount++
                    return@forEachIndexed
                }
                if (photoBytes != null && photoBytes.size > INLINE_PHOTO_LIMIT_BYTES) {
                    if (!writeDisplayPhotoBytes(insertedRawUri, photoBytes)) {
                        contactPhotoFailed = true
                    }
                }
                if (contactPhotoFailed) {
                    failedPhotoCount++
                }
                existingKeys.addAll(dedupKeys)
                restoredCount++
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to import contact ${contact.displayName}", e)
                failedCount++
            }
        }

        ImportedContactsResult(
            restoredCount = restoredCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            failedGroupCount = failedGroupTitles.size,
            failedPhotoCount = failedPhotoCount
        )
    }

    private fun resolveDeviceCountryIso(): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val simOrNetwork = telephony?.simCountryIso?.takeIf { it.length == 2 }
            ?: telephony?.networkCountryIso?.takeIf { it.length == 2 }
        val country = simOrNetwork ?: Locale.getDefault().country.takeIf { it.length == 2 }
        return country?.uppercase(Locale.US)
    }

    private fun loadExistingDedupKeys(
        defaultCountryIso: String?,
        e164Formatter: (String, String) -> String?
    ): Set<String> {
        val phonesByContact = mutableMapOf<Long, MutableList<String>>()
        val emailsByContact = mutableMapOf<Long, MutableList<String>>()
        val namesByContact = mutableMapOf<Long, String>()

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY
            ),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            ),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val cid = cursor.getLong(0)
                val mime = cursor.getString(1) ?: continue
                val data1 = cursor.getString(2)
                val displayName = cursor.getString(3)
                if (!displayName.isNullOrBlank()) namesByContact.putIfAbsent(cid, displayName)
                if (data1.isNullOrBlank()) continue
                when (mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        phonesByContact.getOrPut(cid) { mutableListOf() }.add(data1)
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        emailsByContact.getOrPut(cid) { mutableListOf() }.add(data1)
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE ->
                        namesByContact.putIfAbsent(cid, data1)
                }
            }
        }

        val allIds = phonesByContact.keys + emailsByContact.keys + namesByContact.keys
        return allIds.flatMapTo(HashSet()) { cid ->
            buildContactDedupKeys(
                displayName = namesByContact[cid],
                phones = phonesByContact[cid].orEmpty(),
                emails = emailsByContact[cid].orEmpty(),
                defaultCountryIso = defaultCountryIso,
                e164Formatter = e164Formatter
            )
        }
    }

    private fun ensureLocalGroups(
        groups: List<BackedUpContactGroup>,
        shouldStop: () -> Boolean
    ): EnsureLocalGroupsResult {
        val result = mutableMapOf<String, Long>()
        val failedTitles = linkedSetOf<String>()
        val resolver = context.contentResolver

        if (hasReadContactsPermission()) {
            resolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups.DELETED} = 0 AND ${ContactsContract.Groups.ACCOUNT_NAME} IS NULL AND ${ContactsContract.Groups.ACCOUNT_TYPE} IS NULL",
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)?.trim()
                    if (!title.isNullOrEmpty()) result.putIfAbsent(title, cursor.getLong(0))
                }
            }
        }

        groups.forEach { group ->
            if (shouldStop()) return EnsureLocalGroupsResult(result, failedTitles)
            val title = group.title.trim()
            if (title.isEmpty() || title in result) return@forEach
            val op = ContentProviderOperation.newInsert(ContactsContract.Groups.CONTENT_URI)
                .withValue(ContactsContract.Groups.TITLE, title)
                .withValue(ContactsContract.Groups.ACCOUNT_NAME, null)
                .withValue(ContactsContract.Groups.ACCOUNT_TYPE, null)
                .apply {
                    if (!group.notes.isNullOrEmpty()) withValue(ContactsContract.Groups.NOTES, group.notes)
                }
                .build()
            try {
                val uri = resolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(op)).firstOrNull()?.uri
                val groupId = uri?.let { ContentUris.parseId(it) } ?: -1L
                if (groupId > 0L) {
                    result[title] = groupId
                } else {
                    android.util.Log.w(TAG, "Creating local group '$title' returned no valid URI")
                    failedTitles.add(title)
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to create local group '$title'", e)
                failedTitles.add(title)
            }
        }
        return EnsureLocalGroupsResult(result, failedTitles)
    }

    private fun readDisplayPhotoBytes(rawContactId: Long): ByteArray? {
        val uri = Uri.withAppendedPath(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
            ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY
        )
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.createInputStream().readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun writeDisplayPhotoBytes(rawContactUri: Uri, bytes: ByteArray): Boolean {
        val uri = Uri.withAppendedPath(rawContactUri, ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        return try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "rw")
            if (afd == null) {
                android.util.Log.w(TAG, "DisplayPhoto descriptor was null for $rawContactUri")
                false
            } else {
                afd.use { descriptor ->
                    descriptor.createOutputStream().use { it.write(bytes) }
                }
                true
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to write display photo for $rawContactUri", e)
            false
        }
    }

    companion object {
        const val CONTACTS_FILE_NAME = "contacts.json"
        const val PHOTOS_DIR_NAME = "photos"
        const val CONTACTS_RELATIVE_STAGING_PATH = "device-data/contacts"
        private const val TAG = "ContactsBackupRepository"
        private const val INLINE_PHOTO_LIMIT_BYTES = 250_000
    }
}
