package app.restoid.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A repository for fetching information about installed applications.
 * It uses a two-layer cache: an in-memory cache for speed and a persistent
 * JSON file cache on disk to survive app restarts.
 */
class AppInfoRepository(private val context: Context) {

    // Hot cache: In-memory for fully inflated AppInfo objects with Drawables.
    private val appInfoCache = mutableMapOf<String, AppInfo>()
    // Warm cache: In-memory representation of the JSON on disk.
    private val diskCache = mutableMapOf<String, CachedAppInfo>()

    private val cacheFile = File(context.filesDir, "app_info_cache.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false // Use false for smaller file size
    }
    private val cacheMutex = Mutex()

    init {
        // Load the persistent cache from disk on initialization.
        loadCacheFromDisk()
    }

    private fun loadCacheFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val content = cacheFile.readText()
            if (content.isNotBlank()) {
                val cachedItems = json.decodeFromString<Map<String, CachedAppInfo>>(content)
                diskCache.putAll(cachedItems)
            }
        } catch (e: Exception) {
            // Error reading or parsing cache, it might be corrupted or empty.
            cacheFile.delete()
        }
    }

    private suspend fun saveCacheToDisk() = cacheMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val content = json.encodeToString(diskCache)
                cacheFile.writeText(content)
            } catch (e: Exception) {
                // Handle serialization/write error, e.g., log it.
            }
        }
    }

    /**
     * Fetches AppInfo for a list of package names, utilizing caching.
     */
    suspend fun getAppInfoForPackages(packageNames: List<String>): List<AppInfo> {
        return withContext(Dispatchers.IO) {
            packageNames.mapNotNull { getAppInfo(it) }
        }
    }

    /**
     * Gets all user-installed applications, leveraging the cache.
     * It refreshes the list from the package manager and fetches info for any new apps.
     */
    suspend fun getInstalledUserApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageNamesResult = Shell.cmd("pm list packages -3").exec()
        if (packageNamesResult.isSuccess) {
            val packageNames = packageNamesResult.out.map { it.removePrefix("package:").trim() }
            // This will fetch from cache if available, or from system for new apps.
            val appInfos = getAppInfoForPackages(packageNames)
            return@withContext appInfos.sortedBy { it.name.lowercase() }
        }
        return@withContext emptyList()
    }


    /**
     * Fetches information for a single application.
     * 1. Checks hot (in-memory) cache.
     * 2. Checks warm (disk-backed) cache.
     * 3. Fetches from the system if not found in any cache.
     */
    private suspend fun getAppInfo(packageName: String): AppInfo? {
        // 1. Check hot in-memory cache.
        appInfoCache[packageName]?.let { return it }

        // 2. Check warm disk-backed cache.
        diskCache[packageName]?.let { cachedInfo ->
            val appInfo = cachedInfo.toAppInfo(context)
            appInfoCache[packageName] = appInfo // Promote to hot cache.
            return appInfo
        }

        // 3. Not in any cache, fetch from source using root.
        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val pathResult = Shell.cmd("pm path $packageName").exec()
                if (pathResult.isSuccess && pathResult.out.isNotEmpty()) {
                    val apkPath = pathResult.out.first().removePrefix("package:").trim()
                    val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)
                    packageInfo?.applicationInfo?.let { appInfo ->
                        // Required to load icon and label correctly from an APK path.
                        appInfo.sourceDir = apkPath
                        appInfo.publicSourceDir = apkPath
                        val info = AppInfo(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = appInfo.packageName,
                            versionName = packageInfo.versionName ?: "N/A",
                            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo.versionCode.toLong()
                            },
                            icon = appInfo.loadIcon(pm),
                            apkPath = apkPath
                        )
                        // Update caches and save to disk.
                        appInfoCache[packageName] = info
                        diskCache[packageName] = info.toCachedAppInfo()
                        saveCacheToDisk()
                        return@withContext info
                    }
                }
                null
            } catch (e: Exception) {
                // Package not found or other error.
                null
            }
        }
    }
}

