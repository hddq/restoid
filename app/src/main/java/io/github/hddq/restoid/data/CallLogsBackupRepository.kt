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
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        ) ?: throw IllegalStateException("Call log query returned null cursor")
        cursor.use { c ->
            val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
            val presentationIdx = c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)
            val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
            val newIdx = c.getColumnIndex(CallLog.Calls.NEW)
            val cachedNameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val cachedNumTypeIdx = c.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val cachedNumLabelIdx = c.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            val countryIsoIdx = c.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
            val geocodedLocationIdx = c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
            val featuresIdx = c.getColumnIndex(CallLog.Calls.FEATURES)
            val isReadIdx = c.getColumnIndex(CallLog.Calls.IS_READ)
            val phoneAccountComponentIdx = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            val phoneAccountIdIdx = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
            val voicemailUriIdx = c.getColumnIndex(CallLog.Calls.VOICEMAIL_URI)
            val postDialDigitsIdx = c.getColumnIndex(CallLog.Calls.POST_DIAL_DIGITS)
            val viaNumberIdx = c.getColumnIndex(CallLog.Calls.VIA_NUMBER)
            val screeningAppIdx = c.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME)
            val screeningComponentIdx = c.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME)
            val blockReasonIdx = c.getColumnIndex(CallLog.Calls.BLOCK_REASON)

            while (c.moveToNext() && !shouldStop()) {
                val type = c.getIntOrNull(typeIdx) ?: continue
                val date = c.getLongOrNull(dateIdx) ?: continue
                val duration = c.getLongOrNull(durationIdx) ?: 0L

                entries.add(
                    BackedUpCallLogEntry(
                        number = c.getStringOrNull(numberIdx),
                        presentation = c.getIntOrNull(presentationIdx),
                        type = type,
                        date = date,
                        duration = duration,
                        isNew = c.getIntOrNull(newIdx),
                        cachedName = c.getStringOrNull(cachedNameIdx),
                        cachedNumberType = c.getIntOrNull(cachedNumTypeIdx),
                        cachedNumberLabel = c.getStringOrNull(cachedNumLabelIdx),
                        countryIso = c.getStringOrNull(countryIsoIdx),
                        geocodedLocation = c.getStringOrNull(geocodedLocationIdx),
                        features = c.getIntOrNull(featuresIdx),
                        isRead = c.getIntOrNull(isReadIdx),
                        phoneAccountComponentName = c.getStringOrNull(phoneAccountComponentIdx),
                        phoneAccountId = c.getStringOrNull(phoneAccountIdIdx),
                        voicemailUri = c.getStringOrNull(voicemailUriIdx),
                        postDialDigits = c.getStringOrNull(postDialDigitsIdx),
                        viaNumber = c.getStringOrNull(viaNumberIdx),
                        callScreeningAppName = c.getStringOrNull(screeningAppIdx),
                        callScreeningComponentName = c.getStringOrNull(screeningComponentIdx),
                        blockReason = c.getIntOrNull(blockReasonIdx)
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

        check(hasReadCallLogPermission()) { "READ_CALL_LOG permission is required to deduplicate call logs during restore." }
        val existingKeys = loadExistingDedupKeys(deviceCountryIso, e164Formatter).toMutableSet()

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
                val reloadedKeys = loadExistingDedupKeys(deviceCountryIso, e164Formatter)
                existingKeys.addAll(reloadedKeys)
                pendingEntries.forEach { (entry, key) ->
                    if (shouldStop()) return@forEach
                    if (key in reloadedKeys) {
                        restoredCount++
                        return@forEach
                    }
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
        val cursor = context.contentResolver.query(
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
        ) ?: throw IllegalStateException("Call log query returned null cursor")
        cursor.use { c ->
            while (c.moveToNext()) {
                val number = c.getStringOrNull(0)
                val presentation = c.getIntOrNull(1)
                val type = c.getIntOrNull(2) ?: continue
                val date = c.getLongOrNull(3) ?: continue
                val duration = c.getLongOrNull(4) ?: 0L
                val countryIso = c.getStringOrNull(5)
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
