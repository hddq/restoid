package io.github.hddq.restoid.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.StoragePermissionRepository
import io.github.hddq.restoid.data.TagRepository
import io.github.hddq.restoid.work.OperationWorkRepository

class HomeViewModelFactory(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository,
    private val operationWorkRepository: OperationWorkRepository,
    private val rootRepository: RootRepository,
    private val storagePermissionRepository: StoragePermissionRepository,
    private val tagRepository: TagRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                context,
                repositoriesRepository,
                resticBinaryManager,
                resticRepository,
                appInfoRepository,
                metadataRepository,
                operationWorkRepository,
                rootRepository,
                storagePermissionRepository,
                tagRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
