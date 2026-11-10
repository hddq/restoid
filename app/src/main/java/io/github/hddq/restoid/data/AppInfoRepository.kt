package io.github.hddq.restoid.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import io.github.hddq.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private var isDiskCacheLoaded = false

    private suspend fun ensureDiskCacheLoaded() = cacheMutex.withLock {
        if (isDiskCacheLoaded) return@withLock
        isDiskCacheLoaded = true
        if (!cacheFile.exists()) return@withLock
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
            ensureDiskCacheLoaded()
            var diskCacheDirty = false
            val results = packageNames.mapNotNull { packageName ->
                getAppInfo(packageName) { diskCacheDirty = true }
            }
            if (diskCacheDirty) {
                saveCacheToDisk()
            }
            results
        }
    }

    /**
     * Gets all user-installed applications, leveraging the cache.
     * It refreshes the list from the package manager and fetches info for any new apps.
     */
    suspend fun getInstalledUserApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageNamesResult = Shell.cmd("pm list packages -3").exec()
        if (packageNamesResult.isSuccess) {
            val packageNames = packageNamesResult.out
                .map { it.removePrefix("package:").trim() }
                .filter { it != context.packageName } // Exclude self
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
     * It now includes a version check to invalidate stale cache entries.
     */
    private suspend fun getAppInfo(
        packageName: String,
        onDiskCacheModified: () -> Unit
    ): AppInfo? {
        val pm = context.packageManager
        val currentPackageInfo: PackageInfo = try {
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // App isn't installed. Clean up cache if it exists.
            var wasCached = false
            cacheMutex.withLock {
                if (appInfoCache.remove(packageName) != null) wasCached = true
                if (diskCache.remove(packageName) != null) wasCached = true
            }
            if (wasCached) onDiskCacheModified()
            return null
        }

        val currentVersionCode =
            currentPackageInfo.longVersionCode

        val currentSourceDir = currentPackageInfo.applicationInfo?.sourceDir

        // 1. Check hot in-memory cache.
        if (currentSourceDir != null) {
            val hotCached = cacheMutex.withLock { appInfoCache[packageName] }
            if (hotCached != null && hotCached.versionCode == currentVersionCode && hotCached.apkPaths.contains(currentSourceDir)) {
                return hotCached
            }
        }

        // 2. Check warm disk-backed cache.
        if (currentSourceDir != null) {
            val warmCached = cacheMutex.withLock { diskCache[packageName] }
            if (warmCached != null && warmCached.versionCode == currentVersionCode && warmCached.apkPaths.contains(currentSourceDir)) {
                val appInfo = warmCached.toAppInfo(context)
                return cacheMutex.withLock {
                    val finalInfo = appInfoCache[packageName]?.isSelected?.let { isSelected ->
                        appInfo.copy(isSelected = isSelected)
                    } ?: appInfo
                    appInfoCache[packageName] = finalInfo // Promote to hot cache.
                    finalInfo
                }
            }
        }

        // 3. Not in any cache or cache is stale, so fetch fresh info.
        // We can use the PackageInfo we already retrieved. This is faster and doesn't need root.
        return try {
            currentPackageInfo.applicationInfo?.let { app ->
                val apkPaths = mutableListOf<String>()
                apkPaths.add(app.sourceDir)
                app.splitSourceDirs?.let { apkPaths.addAll(it) }

                val preservedSelection = cacheMutex.withLock { appInfoCache[packageName]?.isSelected } ?: true
                val info = AppInfo(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    versionName = currentPackageInfo.versionName ?: "N/A",
                    versionCode = currentVersionCode,
                    icon = app.loadIcon(pm),
                    apkPaths = apkPaths.distinct(),
                    // Preserve selection state if it was in the hot cache before being found stale
                    isSelected = preservedSelection
                )

                // Update caches and mark disk cache as dirty for batch save.
                cacheMutex.withLock {
                    appInfoCache[packageName] = info
                    diskCache[packageName] = info.toCachedAppInfo()
                }
                onDiskCacheModified()
                info
            }
        } catch (e: Exception) {
            // Something went wrong while building AppInfo.
            null
        }
    }

    /**
     * Returns currently granted dangerous/runtime permissions for a package.
     * These permissions can be restored later using `pm grant`.
     */
    suspend fun getGrantedRuntimePermissions(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val requestedPermissions = packageInfo.requestedPermissions ?: return@withContext emptyList()
        val requestedPermissionFlags = packageInfo.requestedPermissionsFlags ?: IntArray(0)

        requestedPermissions.mapIndexedNotNull { index, permission ->
            val flags = requestedPermissionFlags.getOrElse(index) { 0 }
            val isGranted = (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (!isGranted) return@mapIndexedNotNull null

            val permissionInfo = try {
                pm.getPermissionInfo(permission, 0)
            } catch (_: Exception) {
                return@mapIndexedNotNull null
            }

            if (permissionInfo.protection == PermissionInfo.PROTECTION_DANGEROUS) permission else null
        }.distinct().sorted()
    }
}
