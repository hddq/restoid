package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.work.RunTasksConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskConfigEditorTagsTest {

    @Test
    fun `toRunTasksConfig sanitizes and preserves backupTags`() {
        val state = TaskConfigEditorState(
            backupTags = listOf(" weekly ", "restoid", "bad,tag", "weekly", "photos")
        )

        val config = state.toRunTasksConfig(hasRoot = false)

        assertEquals(listOf("weekly", "photos"), config.backupTags)
    }

    @Test
    fun `withAppliedRunTasksConfig applies sanitized backupTags`() {
        val initial = TaskConfigEditorState(backupTags = listOf("old"))
        val config = RunTasksConfig(
            backupTags = listOf(" important ", "metadata", "important", "work")
        )

        val updated = initial.withAppliedRunTasksConfig(config)

        assertEquals(listOf("important", "work"), updated.backupTags)
    }

    @Test
    fun `hasTaskConfigChanges detects backupTags changes`() {
        val initial = TaskConfigEditorState(backupTags = listOf("daily"))
        val unchanged = TaskConfigEditorState(backupTags = listOf("daily"))
        val changed = TaskConfigEditorState(backupTags = listOf("daily", "cloud"))

        assertFalse(hasTaskConfigChanges(unchanged, initial))
        assertTrue(hasTaskConfigChanges(changed, initial))
    }
}
