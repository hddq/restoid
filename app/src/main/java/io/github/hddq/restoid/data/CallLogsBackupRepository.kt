package io.github.hddq.restoid.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.model.BackedUpCallLogEntry
import io.github.hddq.restoid.model.CallLogsBackupArchive
import io.github.hddq.restoid.model.buildCallLogDedupKey
import io.github.hddq.restoid.model.toDedupKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

data class ExportedCallLogsResult(
    val callLogCount: Int,
    val totalSizeBytes: Long,
    val outputDir: File
)

data class ImportedCallLogsResult(
    val restoredCount: Int,
    val skippedCount: Int,
    val failedCount: Int
)

class CallLogsBackupRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun hasReadCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWriteCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    suspend fun exportCallLogs(
        outputDir: File,
        shouldStop: () -> Boolean = { false }
    ): ExportedCallLogsResult = withContext(Dispatchers.IO) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val entries = mutableListOf<BackedUpCallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val presentationIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val newIdx = cursor.getColumnIndex(CallLog.Calls.NEW)
            val cachedNameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val cachedNumTypeIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val cachedNumLabelIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            val countryIsoIdx = cursor.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
            val geocodedLocationIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
            val featuresIdx = cursor.getColumnIndex(CallLog.Calls.FEATURES)
            val isReadIdx = cursor.getColumnIndex(CallLog.Calls.IS_READ)
            val phoneAccountComponentIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            val phoneAccountIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
            val voicemailUriIdx = cursor.getColumnIndex(CallLog.Calls.VOICEMAIL_URI)
            val postDialDigitsIdx = cursor.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)
            val viaNumberIdx = cursor.getColumnIndex(CallLog.Calls.VIA_NUMBER)
            val screeningAppIdx = cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME)
            val screeningComponentIdx = cursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)
            val blockReasonIdx = cursor.getColumnIndex(CallLog.Calls.BLOCK_REASON)

            while (cursor.moveToNext() && !shouldStop()) {
                val type = cursor.getIntOrNull(typeIdx) ?: continue
                val date = cursor.getLongOrNull(dateIdx) ?: continue
                val duration = cursor.getLongOrNull(durationIdx) ?: 0L

                entries.add(
                    BackedUpCallLogEntry(
                        number = cursor.getStringOrNull(numberIdx),
                        presentation = cursor.getIntOrNull(presentationIdx),
                        type = type,
                        date = date,
                        duration = duration,
                        isNew = cursor.getIntOrNull(newIdx),
                        cachedName = cursor.getStringOrNull(cachedNameIdx),
                        cachedNumberType = cursor.getIntOrNull(cachedNumTypeIdx),
                        cachedNumberLabel = cursor.getStringOrNull(cachedNumLabelIdx),
                        countryIso = cursor.getStringOrNull(countryIsoIdx),
                        geocodedLocation = cursor.getStringOrNull(geocodedLocationIdx),
                        features = cursor.getIntOrNull(featuresIdx),
                        isRead = cursor.getIntOrNull(isReadIdx),
                        phoneAccountComponentName = cursor.getStringOrNull(phoneAccountComponentIdx),
                        phoneAccountId = cursor.getStringOrNull(phoneAccountIdIdx),
                        voicemailUri = cursor.getStringOrNull(voicemailUriIdx),
                        postDialDigits = cursor.getStringOrNull(postDialDigitsIdx),
                        viaNumber = cursor.getStringOrNull(viaNumberIdx),
                        callScreeningAppName = cursor.getStringOrNull(screeningAppIdx),
                        callScreeningComponentName = cursor.getStringOrNull(screeningComponentIdx),
                        blockReason = cursor.getIntOrNull(blockReasonIdx)
                    )
                )
            }
        }

        val archive = CallLogsBackupArchive(
            version = 1,
            calls = entries
        )
        val archiveFile = File(outputDir, CALL_LOGS_FILE_NAME)
        archiveFile.writeText(json.encodeToString(archive))

        val totalSizeBytes = outputDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        ExportedCallLogsResult(
            callLogCount = entries.size,
            totalSizeBytes = totalSizeBytes,
            outputDir = outputDir
        )
    }

    suspend fun importCallLogs(
        inputDir: File,
        shouldStop: () -> Boolean = { false },
        onCallLogProcessed: (processed: Int, total: Int, label: String?) -> Unit = { _, _, _ -> }
    ): ImportedCallLogsResult = withContext(Dispatchers.IO) {
        val archiveFile = File(inputDir, CALL_LOGS_FILE_NAME)
        require(archiveFile.exists()) { "Call logs backup file not found: ${archiveFile.absolutePath}" }

        val archive = json.decodeFromString<CallLogsBackupArchive>(archiveFile.readText())
        val resolver = context.contentResolver
        val deviceCountryIso = resolveDeviceCountryIso()
        val e164Formatter: (String, String) -> String? = { number, country ->
            PhoneNumberUtils.formatNumberToE164(number, country)
        }

        val existingKeys = if (hasReadCallLogPermission()) {
            loadExistingDedupKeys(deviceCountryIso, e164Formatter).toMutableSet()
        } else {
            mutableSetOf()
        }

        var restoredCount = 0
        var skippedCount = 0
        var failedCount = 0
        val total = archive.calls.size
        val pendingEntries = mutableListOf<Pair<BackedUpCallLogEntry, String>>()

        fun flushBatch() {
            if (pendingEntries.isEmpty()) return
            val ops = ArrayList<ContentProviderOperation>(pendingEntries.size)
            pendingEntries.forEach { (entry, _) ->
                ops.add(buildInsertOperation(entry, includeOptionalColumns = true))
            }
            try {
                val results = resolver.applyBatch(CallLog.AUTHORITY, ops)
                if (results.size == pendingEntries.size) {
                    pendingEntries.forEachIndexed { idx, (_, key) ->
                        if (results[idx].uri != null) {
                            existingKeys.add(key)
                            restoredCount++
                        } else {
                            failedCount++
                        }
                    }
                } else {
                    pendingEntries.forEach { (_, key) -> existingKeys.add(key) }
                    restoredCount += pendingEntries.size
                }
            } catch (batchEx: Exception) {
                android.util.Log.w(TAG, "Call log batch insert failed, falling back to single inserts", batchEx)
                pendingEntries.forEach { (entry, key) ->
                    if (shouldStop()) return@forEach
                    val inserted = runCatching {
                        resolver.applyBatch(
                            CallLog.AUTHORITY,
                            arrayListOf(buildInsertOperation(entry, includeOptionalColumns = true))
                        ).firstOrNull()?.uri != null
                    }.getOrElse {
                        runCatching {
                            resolver.applyBatch(
                                CallLog.AUTHORITY,
                                arrayListOf(buildInsertOperation(entry, includeOptionalColumns = false))
                            ).firstOrNull()?.uri != null
                        }.onFailure { singleEx ->
                            android.util.Log.w(TAG, "Failed to import call log entry at ${entry.date}", singleEx)
                        }.getOrDefault(false)
                    }
                    if (inserted) {
                        existingKeys.add(key)
                        restoredCount++
                    } else {
                        failedCount++
                    }
                }
            } finally {
                pendingEntries.clear()
            }
        }

        for ((index, entry) in archive.calls.withIndex()) {
            if (shouldStop()) {
                return@withContext ImportedCallLogsResult(
                    restoredCount = restoredCount,
                    skippedCount = skippedCount,
                    failedCount = failedCount
                )
            }
            val label = entry.cachedName?.takeIf { it.isNotBlank() } ?: entry.number?.takeIf { it.isNotBlank() }
            onCallLogProcessed(index + 1, total, label)

            val dedupKey = entry.toDedupKey(deviceCountryIso, e164Formatter)
            if (!existingKeys.add(dedupKey)) {
                skippedCount++
                continue
            }
            pendingEntries.add(entry to dedupKey)
            if (pendingEntries.size >= INSERT_BATCH_SIZE) {
                flushBatch()
            }
        }

        flushBatch()

        ImportedCallLogsResult(
            restoredCount = restoredCount,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
    }

    private fun buildInsertOperation(
        entry: BackedUpCallLogEntry,
        includeOptionalColumns: Boolean
    ): ContentProviderOperation {
        return ContentProviderOperation.newInsert(CallLog.Calls.CONTENT_URI)
            .withValue(CallLog.Calls.TYPE, entry.type)
            .withValue(CallLog.Calls.DATE, entry.date)
            .withValue(CallLog.Calls.DURATION, entry.duration)
            .apply {
                entry.number?.let { withValue(CallLog.Calls.NUMBER, it) }
                entry.presentation?.let { withValue(CallLog.Calls.NUMBER_PRESENTATION, it) }
                entry.isNew?.let { withValue(CallLog.Calls.NEW, it) }
                entry.cachedName?.let { withValue(CallLog.Calls.CACHED_NAME, it) }
                entry.cachedNumberType?.let { withValue(CallLog.Calls.CACHED_NUMBER_TYPE, it) }
                entry.cachedNumberLabel?.let { withValue(CallLog.Calls.CACHED_NUMBER_LABEL, it) }
                entry.countryIso?.let { withValue(CallLog.Calls.COUNTRY_ISO, it) }
                entry.geocodedLocation?.let { withValue(CallLog.Calls.GEOCODED_LOCATION, it) }
                entry.features?.let { withValue(CallLog.Calls.FEATURES, it) }
                entry.isRead?.let { withValue(CallLog.Calls.IS_READ, it) }
                if (includeOptionalColumns) {
                    entry.phoneAccountComponentName?.let { withValue(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, it) }
                    entry.phoneAccountId?.let { withValue(CallLog.Calls.PHONE_ACCOUNT_ID, it) }
                    entry.voicemailUri?.let { withValue(CallLog.Calls.VOICEMAIL_URI, it) }
                    entry.postDialDigits?.let { withValue(CallLog.Calls.POST_DIAL_DIGITS, it) }
                    entry.viaNumber?.let { withValue(CallLog.Calls.VIA_NUMBER, it) }
                    entry.callScreeningAppName?.let { withValue(CallLog.Calls.CALL_SCREENING_APP_NAME, it) }
                    entry.callScreeningComponentName?.let { withValue(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME, it) }
                    entry.blockReason?.let { withValue(CallLog.Calls.BLOCK_REASON, it) }
                }
            }
            .build()
    }

    private fun loadExistingDedupKeys(
        defaultCountryIso: String?,
        e164Formatter: (String, String) -> String?
    ): Set<String> {
        val keys = HashSet<String>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.NUMBER_PRESENTATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.COUNTRY_ISO
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getStringOrNull(0)
                val presentation = cursor.getIntOrNull(1)
                val type = cursor.getIntOrNull(2) ?: continue
                val date = cursor.getLongOrNull(3) ?: continue
                val duration = cursor.getLongOrNull(4) ?: 0L
                val countryIso = cursor.getStringOrNull(5)
                keys.add(
                    buildCallLogDedupKey(
                        number = number,
                        presentation = presentation,
                        type = type,
                        date = date,
                        duration = duration,
                        defaultCountryIso = countryIso ?: defaultCountryIso,
                        e164Formatter = e164Formatter
                    )
                )
            }
        }
        return keys
    }

    private fun resolveDeviceCountryIso(): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val simOrNetwork = telephony?.simCountryIso?.takeIf { it.length == 2 }
            ?: telephony?.networkCountryIso?.takeIf { it.length == 2 }
        val country = simOrNetwork ?: Locale.getDefault().country.takeIf { it.length == 2 }
        return country?.uppercase(Locale.US)
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.getIntOrNull(index: Int): Int? =
        if (index < 0 || isNull(index)) null else getInt(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (index < 0 || isNull(index)) null else getLong(index)

    companion object {
        const val CALL_LOGS_FILE_NAME = "call_logs.json"
        const val CALL_LOGS_RELATIVE_STAGING_PATH = "device-data/call-logs"
        private const val TAG = "CallLogsBackupRepository"
        private const val INSERT_BATCH_SIZE = 100
    }
}
