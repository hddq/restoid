package io.github.hddq.restoid.ui.backup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.work.OperationWorkRepository

class BackupViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(
                application,
                repositoriesRepository,
                resticBinaryManager,
                appInfoRepository,
                preferencesRepository,
                operationWorkRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
