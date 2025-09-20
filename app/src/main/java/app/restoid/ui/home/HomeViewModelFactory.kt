package app.restoid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.AppInfoRepository
import app.restoid.data.MetadataRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository

class HomeViewModelFactory(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repositoriesRepository, resticRepository, appInfoRepository, metadataRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

