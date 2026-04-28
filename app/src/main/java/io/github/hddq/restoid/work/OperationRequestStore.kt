package io.github.hddq.restoid.work

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class OperationRequestStore(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "operation_requests")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    fun saveBackupRequest(request: BackupWorkRequest): String {
        return saveRequest(OperationRequestType.BACKUP, json.encodeToString(request))
    }

    fun saveRestoreRequest(request: RestoreWorkRequest): String {
        return saveRequest(OperationRequestType.RESTORE, json.encodeToString(request))
    }

    fun saveMaintenanceRequest(request: MaintenanceWorkRequest): String {
        return saveRequest(OperationRequestType.MAINTENANCE, json.encodeToString(request))
    }

    fun saveRunTasksRequest(request: RunTasksWorkRequest): String {
        return saveRequest(OperationRequestType.RUN_TASKS, json.encodeToString(request))
    }

    fun loadBackupRequest(requestId: String): BackupWorkRequest {
        val content = loadRequestContent(requestId, OperationRequestType.BACKUP)
        return json.decodeFromString(content)
    }

    fun loadRestoreRequest(requestId: String): RestoreWorkRequest {
        val content = loadRequestContent(requestId, OperationRequestType.RESTORE)
        return json.decodeFromString(content)
    }

    fun loadMaintenanceRequest(requestId: String): MaintenanceWorkRequest {
        val content = loadRequestContent(requestId, OperationRequestType.MAINTENANCE)
        return json.decodeFromString(content)
    }

    fun loadRunTasksRequest(requestId: String): RunTasksWorkRequest {
        val content = loadRequestContent(requestId, OperationRequestType.RUN_TASKS)
        return json.decodeFromString(content)
    }

    fun deleteRequest(requestId: String) {
        rootDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$requestId.")) {
                file.delete()
            }
        }
    }

    private fun saveRequest(type: OperationRequestType, payload: String): String {
        val requestId = UUID.randomUUID().toString()
        val file = requestFile(requestId, type)
        file.writeText(payload)
        return requestId
    }

    private fun loadRequestContent(requestId: String, type: OperationRequestType): String {
        val file = requestFile(requestId, type)
        if (!file.exists()) {
            throw IllegalStateException("Operation request not found: $requestId (${type.name})")
        }
        return file.readText()
    }

    private fun requestFile(requestId: String, type: OperationRequestType): File {
        return File(rootDir, "$requestId.${type.fileSuffix}.json")
    }
}

enum class OperationRequestType(val fileSuffix: String) {
    BACKUP("backup"),
    RESTORE("restore"),
    MAINTENANCE("maintenance"),
    RUN_TASKS("run_tasks")
}
