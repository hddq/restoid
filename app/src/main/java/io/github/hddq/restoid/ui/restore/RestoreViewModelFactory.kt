package io.github.hddq.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.work.OperationWorkRepository

class RestoreViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository,
    private val snapshotId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RestoreViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RestoreViewModel(
                application,
                repositoriesRepository,
                resticBinaryManager,
                resticRepository,
                appInfoRepository,
                metadataRepository,
                preferencesRepository,
                operationWorkRepository,
                snapshotId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
