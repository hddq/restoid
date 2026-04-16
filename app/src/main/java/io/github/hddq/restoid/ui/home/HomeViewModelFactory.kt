package io.github.hddq.restoid.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository

class HomeViewModelFactory(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(context, repositoriesRepository, resticBinaryManager, resticRepository, appInfoRepository, metadataRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
