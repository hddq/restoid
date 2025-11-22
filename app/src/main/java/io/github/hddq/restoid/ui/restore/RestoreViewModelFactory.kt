package io.github.hddq.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.*

class RestoreViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val notificationRepository: NotificationRepository,
    private val metadataRepository: MetadataRepository,
    private val preferencesRepository: PreferencesRepository,
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
                notificationRepository,
                metadataRepository,
                preferencesRepository,
                snapshotId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}