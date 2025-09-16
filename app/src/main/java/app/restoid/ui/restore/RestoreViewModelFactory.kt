package app.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.AppInfoRepository
import app.restoid.data.NotificationRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository

class RestoreViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val notificationRepository: NotificationRepository,
    private val snapshotId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RestoreViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RestoreViewModel(
                application,
                repositoriesRepository,
                resticRepository,
                appInfoRepository,
                notificationRepository,
                snapshotId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
