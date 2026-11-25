package io.github.hddq.restoid.ui.schedules

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import io.github.hddq.restoid.ui.shared.hasTaskConfigChanges
import io.github.hddq.restoid.ui.shared.sortApps
import io.github.hddq.restoid.work.RunTasksConfig
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
    fun hasTaskConfigChanges_ignoresAppSortOrderChanges() {
        val alpha = app("Alpha", "com.example.alpha", isSelected = true, firstInstallTime = 100L)
        val beta = app("Beta", "com.example.beta", isSelected = false, firstInstallTime = 200L)
        val initial = TaskConfigEditorState(
            apps = listOf(alpha, beta),
            appSortOrder = AppSortOrder.NAME_ASCENDING
        )

        val reordered = initial.copy(
            appSortOrder = AppSortOrder.NAME_DESCENDING,
            apps = sortApps(initial.apps, AppSortOrder.NAME_DESCENDING)
        )

        assertFalse(hasTaskConfigChanges(reordered, initial))
    }

    @Test
    fun hasTaskConfigChanges_detectsSelectionChangeAfterReordering() {
        val alpha = app("Alpha", "com.example.alpha", isSelected = true, firstInstallTime = 100L)
        val beta = app("Beta", "com.example.beta", isSelected = false, firstInstallTime = 200L)
        val initial = TaskConfigEditorState(
            apps = listOf(alpha, beta),
            appSortOrder = AppSortOrder.NAME_ASCENDING
        )

        val toggled = initial.copy(
            appSortOrder = AppSortOrder.NAME_DESCENDING,
            apps = sortApps(initial.apps, AppSortOrder.NAME_DESCENDING).map {
                if (it.packageName == "com.example.beta") it.copy(isSelected = true) else it
            }
        )

        assertTrue(hasTaskConfigChanges(toggled, initial))
    }

    @Test
    fun hasScheduleChanges_detectsPresetTimingLabelAndConditionsChanges() {
        val initial = AddEditScheduleUiState(
            id = "s-1",
            presetId = "p-1",
            name = "Nightly",
            intervalHours = 24,
            isEnabled = true,
            triggerConditions = TriggerConditions(requireUnmeteredNetwork = true)
        )

        assertFalse(
            hasScheduleChanges(
                initial.copy(
                    availablePresets = listOf(TaskPreset(id = "p-1", name = "A", config = RunTasksConfig())),
                    isSaving = true,
                    showConfirmDeleteDialog = true
                ),
                initial
            )
        )
        assertTrue(hasScheduleChanges(initial.copy(presetId = "p-2"), initial))
        assertTrue(hasScheduleChanges(initial.copy(name = "Morning"), initial))
        assertTrue(hasScheduleChanges(initial.copy(intervalHours = 12), initial))
        assertTrue(hasScheduleChanges(initial.copy(isEnabled = false), initial))
        assertTrue(
            hasScheduleChanges(
                initial.copy(triggerConditions = TriggerConditions(requireCharging = true)),
                initial
            )
        )
    }
}
