package io.github.hddq.restoid.ui.shared

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDetailSortingTest {

    private fun app(
        name: String,
        packageName: String = name.lowercase(),
        firstInstallTime: Long
    ): AppInfo = AppInfo(
        name = name,
        packageName = packageName,
        versionName = "1.0",
        versionCode = 1L,
        icon = object : Drawable() {
            override fun draw(canvas: Canvas) {}
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.UNKNOWN
        },
        apkPaths = emptyList(),
        firstInstallTime = firstInstallTime
    )

    @Test
    fun sortApps_byFirstInstallTime_ordersNewestFirst() {
        val apps = listOf(
            app(name = "Alpha", firstInstallTime = 100L),
            app(name = "Beta", firstInstallTime = 300L),
            app(name = "Gamma", firstInstallTime = 200L)
        )

        assertEquals(
            listOf("Beta", "Gamma", "Alpha"),
            sortApps(apps, AppSortOrder.FIRST_INSTALL_TIME_DESCENDING).map { it.name }
        )
        assertEquals(
            listOf("Alpha", "Gamma", "Beta"),
            sortApps(apps, AppSortOrder.FIRST_INSTALL_TIME_ASCENDING).map { it.name }
        )
    }

    @Test
    fun sortBackupDetails_byFirstInstallTime_ordersNewestFirst() {
        val details = listOf(
            BackupDetail(app("Alpha", firstInstallTime = 100L), emptyList(), isDowngrade = false, isInstalled = true),
            BackupDetail(app("Beta", firstInstallTime = 300L), emptyList(), isDowngrade = false, isInstalled = true),
            BackupDetail(app("Gamma", firstInstallTime = 200L), emptyList(), isDowngrade = false, isInstalled = true)
        )

        assertEquals(
            listOf("Beta", "Gamma", "Alpha"),
            sortBackupDetails(details, AppSortOrder.FIRST_INSTALL_TIME_DESCENDING).map { it.appInfo.name }
        )
        assertEquals(
            listOf("Alpha", "Gamma", "Beta"),
            sortBackupDetails(details, AppSortOrder.FIRST_INSTALL_TIME_ASCENDING).map { it.appInfo.name }
        )
    }
}
