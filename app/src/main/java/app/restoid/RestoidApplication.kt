package app.restoid

import android.app.Application
import app.restoid.data.ResticRepository
import app.restoid.data.RootRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RestoidApplication : Application() {

    // Use a proper scope that is cancelled when the app is destroyed
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Create a single instance of the repositories for the whole app
    lateinit var rootRepository: RootRepository
        private set
    lateinit var resticRepository: ResticRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize repositories
        rootRepository = RootRepository()
        resticRepository = ResticRepository(applicationContext)

        // Start the initial status checks on a background thread
        applicationScope.launch {
            rootRepository.checkRootAccess()
            resticRepository.checkResticStatus()
        }
    }
}
