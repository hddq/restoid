package app.restoid.ui.snapshot

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.AppInfoRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository

class SnapshotDetailsViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapshotDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapshotDetailsViewModel(application, repositoriesRepository, resticRepository, appInfoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
