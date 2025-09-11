// file: app/restoid/RestoidApplication.kt
package app.restoid

import android.app.Application
import app.restoid.data.RootRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RestoidApplication : Application() {

    // Create a single instance of the repository for the whole app
    lateinit var rootRepository: RootRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize the repository
        rootRepository = RootRepository()
        // Start the root check immediately on a background thread
        GlobalScope.launch {
            rootRepository.checkRootAccess()
        }
    }
}