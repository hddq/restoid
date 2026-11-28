package io.github.hddq.restoid.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogDialogTest {

    @Test
    fun parseChangelogItems_stripsBulletsAndBlankLines() {
        val raw = """
            * Select specific calendars and contact accounts during restore

            - Show live condition status for backup schedules
            • Plain bullet item
            Unprefixed note
        """.trimIndent()

        assertEquals(
            listOf(
                "Select specific calendars and contact accounts during restore",
                "Show live condition status for backup schedules",
                "Plain bullet item",
                "Unprefixed note"
            ),
            parseChangelogItems(raw)
        )
    }

    @Test
    fun parseChangelogItems_returnsEmptyForBlankInput() {
        assertEquals(emptyList<String>(), parseChangelogItems("   \n\t\n "))
    }
}
