package io.github.hddq.restoid.ui.schedules

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.sortApps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulesViewModelChangesTest {

    private fun app(
        name: String,
        packageName: String,
        isSelected: Boolean,
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
        isSelected = isSelected,
        firstInstallTime = firstInstallTime
    )

    @Test
    fun hasScheduleChanges_ignoresAppSortOrderChanges() {
        val alpha = app("Alpha", "com.example.alpha", isSelected = true, firstInstallTime = 100L)
        val beta = app("Beta", "com.example.beta", isSelected = false, firstInstallTime = 200L)
        val initial = AddEditScheduleUiState(
            name = "Nightly",
            apps = listOf(alpha, beta),
            appSortOrder = AppSortOrder.NAME_ASCENDING
        )

        val reordered = initial.copy(
            appSortOrder = AppSortOrder.NAME_DESCENDING,
            apps = sortApps(initial.apps, AppSortOrder.NAME_DESCENDING)
        )

        assertFalse(hasScheduleChanges(reordered, initial))
    }

    @Test
    fun hasScheduleChanges_detectsSelectionChangeAfterReordering() {
        val alpha = app("Alpha", "com.example.alpha", isSelected = true, firstInstallTime = 100L)
        val beta = app("Beta", "com.example.beta", isSelected = false, firstInstallTime = 200L)
        val initial = AddEditScheduleUiState(
            name = "Nightly",
            apps = listOf(alpha, beta),
            appSortOrder = AppSortOrder.NAME_ASCENDING
        )

        val toggled = initial.copy(
            appSortOrder = AppSortOrder.NAME_DESCENDING,
            apps = sortApps(initial.apps, AppSortOrder.NAME_DESCENDING).map {
                if (it.packageName == "com.example.beta") it.copy(isSelected = true) else it
            }
        )

        assertTrue(hasScheduleChanges(toggled, initial))
    }
}
