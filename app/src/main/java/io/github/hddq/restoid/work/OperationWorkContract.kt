package io.github.hddq.restoid.work

import androidx.work.Data
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.shared.OperationProgress

object OperationWorkContract {
    const val UNIQUE_WORK_NAME = "restoid_heavy_operation"
    const val TAG_HEAVY_OPERATION = "restoid_heavy_operation"

    const val INPUT_OPERATION_TYPE = "input_operation_type"
    const val INPUT_REQUEST_ID = "input_request_id"

    private const val KEY_OPERATION_TYPE = "operation_type"
    private const val KEY_STAGE_TITLE = "stage_title"
    private const val KEY_STAGE_PERCENTAGE = "stage_percentage"
    private const val KEY_OVERALL_PERCENTAGE = "overall_percentage"
    private const val KEY_TOTAL_FILES = "total_files"
    private const val KEY_FILES_PROCESSED = "files_processed"
    private const val KEY_TOTAL_BYTES = "total_bytes"
    private const val KEY_BYTES_PROCESSED = "bytes_processed"
    private const val KEY_CURRENT_FILE = "current_file"
    private const val KEY_ELAPSED_TIME = "elapsed_time"
    private const val KEY_ERROR = "error"
    private const val KEY_IS_FINISHED = "is_finished"
    private const val KEY_FINAL_SUMMARY = "final_summary"
    private const val KEY_SNAPSHOT_ID = "snapshot_id"
    private const val KEY_FILES_NEW = "files_new"
    private const val KEY_FILES_CHANGED = "files_changed"
    private const val KEY_DATA_ADDED = "data_added"
    private const val KEY_TOTAL_DURATION = "total_duration"
    private const val KEY_SUCCESS = "success"

    private const val MAX_SUMMARY_LENGTH = 3500

    fun parseOperationType(raw: String?): OperationType? {
        return raw?.let { value -> runCatching { OperationType.valueOf(value) }.getOrNull() }
    }

    fun progressToData(operationType: OperationType, progress: OperationProgress): Data {
        return Data.Builder()
            .putString(KEY_OPERATION_TYPE, operationType.name)
            .putString(KEY_STAGE_TITLE, progress.stageTitle)
            .putFloat(KEY_STAGE_PERCENTAGE, progress.stagePercentage)
            .putFloat(KEY_OVERALL_PERCENTAGE, progress.overallPercentage)
            .putInt(KEY_TOTAL_FILES, progress.totalFiles)
            .putInt(KEY_FILES_PROCESSED, progress.filesProcessed)
            .putLong(KEY_TOTAL_BYTES, progress.totalBytes)
            .putLong(KEY_BYTES_PROCESSED, progress.bytesProcessed)
            .putString(KEY_CURRENT_FILE, progress.currentFile)
            .putLong(KEY_ELAPSED_TIME, progress.elapsedTime)
            .putString(KEY_ERROR, progress.error)
            .putBoolean(KEY_IS_FINISHED, progress.isFinished)
            .putString(KEY_FINAL_SUMMARY, progress.finalSummary.take(MAX_SUMMARY_LENGTH))
            .putString(KEY_SNAPSHOT_ID, progress.snapshotId)
            .putInt(KEY_FILES_NEW, progress.filesNew)
            .putInt(KEY_FILES_CHANGED, progress.filesChanged)
            .putLong(KEY_DATA_ADDED, progress.dataAdded)
            .putDouble(KEY_TOTAL_DURATION, progress.totalDuration)
            .build()
    }

    fun outputToData(operationType: OperationType, success: Boolean, progress: OperationProgress): Data {
        return Data.Builder()
            .putAll(progressToData(operationType, progress))
            .putBoolean(KEY_SUCCESS, success)
            .build()
    }

    fun operationTypeFromData(data: Data): OperationType? {
        return parseOperationType(data.getString(KEY_OPERATION_TYPE))
    }

    fun progressFromData(data: Data): OperationProgress {
        return OperationProgress(
            stageTitle = data.getString(KEY_STAGE_TITLE).orEmpty(),
            stagePercentage = data.getFloat(KEY_STAGE_PERCENTAGE, 0f),
            overallPercentage = data.getFloat(KEY_OVERALL_PERCENTAGE, 0f),
            totalFiles = data.getInt(KEY_TOTAL_FILES, 0),
            filesProcessed = data.getInt(KEY_FILES_PROCESSED, 0),
            totalBytes = data.getLong(KEY_TOTAL_BYTES, 0L),
            bytesProcessed = data.getLong(KEY_BYTES_PROCESSED, 0L),
            currentFile = data.getString(KEY_CURRENT_FILE).orEmpty(),
            elapsedTime = data.getLong(KEY_ELAPSED_TIME, 0L),
            error = data.getString(KEY_ERROR),
            isFinished = data.getBoolean(KEY_IS_FINISHED, false),
            finalSummary = data.getString(KEY_FINAL_SUMMARY).orEmpty(),
            snapshotId = data.getString(KEY_SNAPSHOT_ID),
            filesNew = data.getInt(KEY_FILES_NEW, 0),
            filesChanged = data.getInt(KEY_FILES_CHANGED, 0),
            dataAdded = data.getLong(KEY_DATA_ADDED, 0L),
            totalDuration = data.getDouble(KEY_TOTAL_DURATION, 0.0)
        )
    }

    fun successFromData(data: Data): Boolean {
        return data.getBoolean(KEY_SUCCESS, false)
    }
}
