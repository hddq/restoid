package app.restoid.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A repository for fetching information about installed applications using their package names.
 * It uses a cache to avoid repeatedly querying the package manager.
 */
class AppInfoRepository(private val context: Context) {

    private val appInfoCache = mutableMapOf<String, AppInfo>()

    /**
     * Fetches AppInfo for a list of package names.
     */
    suspend fun getAppInfoForPackages(packageNames: List<String>): List<AppInfo> {
        return withContext(Dispatchers.IO) {
            packageNames.mapNotNull { getAppInfo(it) }
        }
    }

    /**
     * Fetches information for a single application.
     * Uses root to get the APK path to load the icon and label correctly.
     */
    private suspend fun getAppInfo(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        if (appInfoCache.containsKey(packageName)) {
            return@withContext appInfoCache[packageName]
        }
        try {
            val pm = context.packageManager
            val pathResult = Shell.cmd("pm path $packageName").exec()
            if (pathResult.isSuccess && pathResult.out.isNotEmpty()) {
                val apkPath = pathResult.out.first().removePrefix("package:").trim()
                val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)
                packageInfo?.applicationInfo?.let { appInfo ->
                    // Required to load icon and label correctly from an APK path
                    appInfo.sourceDir = apkPath
                    appInfo.publicSourceDir = apkPath
                    val info = AppInfo(
                        name = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm),
                        apkPath = apkPath
                    )
                    appInfoCache[packageName] = info
                    return@withContext info
                }
            }
            null
        } catch (e: Exception) {
            // Package not found or other error
            null
        }
    }
}
