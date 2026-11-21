package io.github.hddq.restoid.ui.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.buildCalendarSelectionKey
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDataConfigScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingContactsOpensBottomSheetWithReadOnlyContactsToggle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contactsTitle = context.getString(R.string.device_data_contacts)
        val callLogsTitle = context.getString(R.string.device_data_call_logs)
        val includeReadOnlyTitle = context.getString(R.string.device_data_include_read_only_contacts)

        var includeReadOnly by mutableStateOf(false)

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                DeviceDataConfigScreen(
                    contactsEnabled = false,
                    includeReadOnlyContacts = includeReadOnly,
                    callLogsEnabled = false,
                    onContactsEnabledChange = {},
                    onIncludeReadOnlyContactsChange = { includeReadOnly = it },
                    onCallLogsEnabledChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(contactsTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(callLogsTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(includeReadOnlyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithTag(INCLUDE_READ_ONLY_CONTACTS_ROW_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithText(contactsTitle).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(INCLUDE_READ_ONLY_CONTACTS_ROW_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(INCLUDE_READ_ONLY_CONTACTS_ROW_TAG).performClick()
        composeTestRule.waitForIdle()

        assertTrue(includeReadOnly)

        composeTestRule.onNodeWithTag(INCLUDE_READ_ONLY_CONTACTS_ROW_TAG).performClick()
        composeTestRule.waitForIdle()

        assertFalse(includeReadOnly)
    }

    @Test
    fun tappingCalendarOpensBottomSheetAndTogglesCalendarSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val calendarTitle = context.getString(R.string.device_data_calendar)
        val allCalendarsSubtitle = context.getString(R.string.device_data_calendar_all)
        val noneSelectedSubtitle = context.getString(R.string.device_data_calendar_none_selected)
        val oneSelectedSubtitle = context.resources.getQuantityString(
            R.plurals.device_data_calendars_selected_count,
            1,
            1
        )

        val personalKey = buildCalendarSelectionKey(
            accountType = "com.google",
            accountName = "alice@example.com",
            name = "personal",
            displayName = "Personal"
        )
        val workKey = buildCalendarSelectionKey(
            accountType = "LOCAL",
            accountName = "Restoid",
            name = "work",
            displayName = "Work"
        )
        val sampleCalendars = listOf(
            CalendarInfo(
                id = 1L,
                selectionKey = personalKey,
                name = "personal",
                displayName = "Personal",
                accountName = "alice@example.com",
                accountType = "com.google",
                color = 0xFF1E88E5.toInt(),
                timeZone = "UTC",
                isReadOnly = false
            ),
            CalendarInfo(
                id = 2L,
                selectionKey = workKey,
                name = "work",
                displayName = "Work",
                accountName = "Restoid",
                accountType = "LOCAL",
                color = 0xFF43A047.toInt(),
                timeZone = "UTC",
                isReadOnly = true
            )
        )

        var selectAllCalendars by mutableStateOf(true)
        var selectedCalendarKeys by mutableStateOf(emptySet<String>())

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                DeviceDataConfigScreen(
                    contactsEnabled = false,
                    includeReadOnlyContacts = false,
                    callLogsEnabled = false,
                    calendarEnabled = false,
                    selectAllCalendars = selectAllCalendars,
                    selectedCalendarKeys = selectedCalendarKeys,
                    availableCalendars = sampleCalendars,
                    onContactsEnabledChange = {},
                    onIncludeReadOnlyContactsChange = {},
                    onCallLogsEnabledChange = {},
                    onCalendarEnabledChange = {},
                    onToggleAllCalendars = {
                        if (selectAllCalendars) {
                            selectAllCalendars = false
                            selectedCalendarKeys = emptySet()
                        } else {
                            selectAllCalendars = true
                            selectedCalendarKeys = emptySet()
                        }
                    },
                    onToggleCalendar = { key ->
                        val baseSelection = if (selectAllCalendars) {
                            sampleCalendars.map { it.selectionKey }.toSet()
                        } else {
                            selectedCalendarKeys
                        }
                        val nextSelection = if (key in baseSelection) {
                            baseSelection - key
                        } else {
                            baseSelection + key
                        }
                        val allKeys = sampleCalendars.map { it.selectionKey }.toSet()
                        if (nextSelection.containsAll(allKeys)) {
                            selectAllCalendars = true
                            selectedCalendarKeys = emptySet()
                        } else {
                            selectAllCalendars = false
                            selectedCalendarKeys = nextSelection
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(calendarTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(allCalendarsSubtitle).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithText(calendarTitle).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("$CALENDAR_OPTION_ROW_TAG_PREFIX$personalKey").assertIsDisplayed()
        composeTestRule.onNodeWithTag("$CALENDAR_OPTION_ROW_TAG_PREFIX$workKey").assertIsDisplayed()

        composeTestRule.onNodeWithTag("$CALENDAR_OPTION_ROW_TAG_PREFIX$workKey").performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllCalendars)
        assertEquals(setOf(personalKey), selectedCalendarKeys)
        composeTestRule.onNodeWithText(oneSelectedSubtitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()

        assertTrue(selectAllCalendars)
        assertTrue(selectedCalendarKeys.isEmpty())

        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllCalendars)
        assertTrue(selectedCalendarKeys.isEmpty())
        composeTestRule.onNodeWithText(noneSelectedSubtitle).assertIsDisplayed()
    }
}
