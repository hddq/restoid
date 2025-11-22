package io.github.hddq.restoid

import android.app.Application
import io.github.hddq.restoid.data.*
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RestoidApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var rootRepository: RootRepository
        private set
    lateinit var resticBinaryManager: ResticBinaryManager
        private set
    lateinit var resticExecutor: ResticExecutor
        private set
    lateinit var resticRepository: ResticRepository
        private set
    lateinit var repositoriesRepository: RepositoriesRepository
        private set
    lateinit var notificationRepository: NotificationRepository
        private set
    lateinit var appInfoRepository: AppInfoRepository
        private set
    lateinit var passwordManager: PasswordManager
        private set
    lateinit var metadataRepository: MetadataRepository
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()

        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
        )

        // Initialize specialized components
        rootRepository = RootRepository()
        resticBinaryManager = ResticBinaryManager(applicationContext)
        resticExecutor = ResticExecutor(applicationContext, resticBinaryManager)
        resticRepository = ResticRepository(applicationContext, resticExecutor)

        passwordManager = PasswordManager(applicationContext)
        // RepositoriesRepository now needs BinaryManager to check installation status
        repositoriesRepository = RepositoriesRepository(applicationContext, passwordManager, resticBinaryManager)

        notificationRepository = NotificationRepository(applicationContext)
        appInfoRepository = AppInfoRepository(applicationContext)
        metadataRepository = MetadataRepository(applicationContext)
        preferencesRepository = PreferencesRepository(applicationContext)

        passwordManager.clearTemporaryPasswords()
        notificationRepository.createNotificationChannels()

        applicationScope.launch {
            rootRepository.checkRootAccess()
            resticBinaryManager.checkResticStatus() // New manager handles checks
            repositoriesRepository.loadRepositories()
            notificationRepository.checkPermissionStatus()
        }
    }
}