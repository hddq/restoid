package io.github.hddq.restoid.ui.snapshot

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository

class SnapshotDetailsViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapshotDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapshotDetailsViewModel(application, repositoriesRepository, resticRepository, appInfoRepository, metadataRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
