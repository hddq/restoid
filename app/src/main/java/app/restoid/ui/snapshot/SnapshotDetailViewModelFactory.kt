package app.restoid.ui.snapshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository

class SnapshotDetailsViewModelFactory(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapshotDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapshotDetailsViewModel(repositoriesRepository, resticRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
