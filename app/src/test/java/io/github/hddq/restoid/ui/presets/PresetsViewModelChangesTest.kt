package io.github.hddq.restoid.ui.presets

import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsViewModelChangesTest {

    @Test
    fun `hasPresetChanges detects backupTags edits`() {
        val initial = AddEditPresetUiState(
            id = "p-1",
            name = "Nightly",
            editor = TaskConfigEditorState(backupTags = listOf("weekly"))
        )
        val unchanged = initial.copy(
            editor = initial.editor.copy(backupTags = listOf("weekly"))
        )
        val changed = initial.copy(
            editor = initial.editor.copy(backupTags = listOf("weekly", "photos"))
        )

        assertFalse(hasPresetChanges(unchanged, initial))
        assertTrue(hasPresetChanges(changed, initial))
    }
}
