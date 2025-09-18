package app.restoid

import android.app.Application
import app.restoid.data.AppInfoRepository
import app.restoid.data.NotificationRepository
import app.restoid.data.PasswordManager
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.RootRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RestoidApplication : Application() {

    // Use a proper scope that is cancelled when the app is destroyed
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Create single instances of the repositories for the whole app
    lateinit var rootRepository: RootRepository
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

    override fun onCreate() {
        super.onCreate()

        // Configure libsu for a more stable root environment.
        // This is crucial for avoiding SELinux/namespace issues.
        // FLAG_MOUNT_MASTER gives the shell a clean mount namespace.
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
        )

        // Initialize repositories
        rootRepository = RootRepository()
        resticRepository = ResticRepository(applicationContext)
        passwordManager = PasswordManager(applicationContext)
        repositoriesRepository = RepositoriesRepository(applicationContext, passwordManager)
        notificationRepository = NotificationRepository(applicationContext)
        appInfoRepository = AppInfoRepository(applicationContext)

        // Clear any temporary passwords from previous sessions
        passwordManager.clearTemporaryPasswords()

        // Create notification channels on app start
        notificationRepository.createNotificationChannels()

        // Start initial status checks and load data on a background thread
        applicationScope.launch {
            rootRepository.checkRootAccess()
            resticRepository.checkResticStatus()
            repositoriesRepository.loadRepositories()
            notificationRepository.checkPermissionStatus()
        }
    }
}
