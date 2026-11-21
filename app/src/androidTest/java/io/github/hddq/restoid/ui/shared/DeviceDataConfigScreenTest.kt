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
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.model.buildCalendarSelectionKey
import io.github.hddq.restoid.model.buildContactAccountSelectionKey
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
    fun tappingContactsOpensBottomSheetAndTogglesContactAccountSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contactsTitle = context.getString(R.string.device_data_contacts)
        val callLogsTitle = context.getString(R.string.device_data_call_logs)
        val allAccountsSubtitle = context.getString(R.string.device_data_contacts_all_accounts)
        val noneSelectedSubtitle = context.getString(R.string.device_data_contacts_none_selected)
        val readOnlyLabel = context.getString(R.string.device_data_contacts_read_only)
        val twoSelectedSubtitle = context.resources.getQuantityString(
            R.plurals.device_data_contacts_accounts_selected_count,
            2,
            2
        )

        val localKey = buildContactAccountSelectionKey(accountType = null, accountName = null)
        val googleKey = buildContactAccountSelectionKey(
            accountType = "com.google",
            accountName = "alice@example.com"
        )
        val whatsappKey = buildContactAccountSelectionKey(
            accountType = "com.whatsapp",
            accountName = "WhatsApp"
        )
        val sampleAccounts = listOf(
            ContactAccountInfo(
                selectionKey = localKey,
                accountName = "",
                accountType = "",
                displayName = context.getString(R.string.device_data_contacts_local_account),
                accountTypeLabel = null,
                isLocal = true,
                isReadOnly = false
            ),
            ContactAccountInfo(
                selectionKey = googleKey,
                accountName = "alice@example.com",
                accountType = "com.google",
                displayName = "alice@example.com",
                accountTypeLabel = "Google",
                isLocal = false,
                isReadOnly = false
            ),
            ContactAccountInfo(
                selectionKey = whatsappKey,
                accountName = "WhatsApp",
                accountType = "com.whatsapp",
                displayName = "WhatsApp",
                accountTypeLabel = "WhatsApp",
                isLocal = false,
                isReadOnly = true
            )
        )

        var selectAllContactAccounts by mutableStateOf(true)
        var selectedContactAccountKeys by mutableStateOf(emptySet<String>())

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                DeviceDataConfigScreen(
                    contactsEnabled = false,
                    selectAllContactAccounts = selectAllContactAccounts,
                    selectedContactAccountKeys = selectedContactAccountKeys,
                    availableContactAccounts = sampleAccounts,
                    callLogsEnabled = false,
                    onContactsEnabledChange = {},
                    onToggleAllContactAccounts = {
                        if (selectAllContactAccounts) {
                            selectAllContactAccounts = false
                            selectedContactAccountKeys = emptySet()
                        } else {
                            selectAllContactAccounts = true
                            selectedContactAccountKeys = emptySet()
                        }
                    },
                    onToggleContactAccount = { key ->
                        val baseSelection = if (selectAllContactAccounts) {
                            sampleAccounts.map { it.selectionKey }.toSet()
                        } else {
                            selectedContactAccountKeys
                        }
                        val nextSelection = if (key in baseSelection) {
                            baseSelection - key
                        } else {
                            baseSelection + key
                        }
                        val allKeys = sampleAccounts.map { it.selectionKey }.toSet()
                        if (nextSelection.containsAll(allKeys)) {
                            selectAllContactAccounts = true
                            selectedContactAccountKeys = emptySet()
                        } else {
                            selectAllContactAccounts = false
                            selectedContactAccountKeys = nextSelection
                        }
                    },
                    onCallLogsEnabledChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(contactsTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(callLogsTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(allAccountsSubtitle).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithText(contactsTitle).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("$CONTACTS_OPTION_ROW_TAG_PREFIX$localKey").assertIsDisplayed()
        composeTestRule.onNodeWithTag("$CONTACTS_OPTION_ROW_TAG_PREFIX$googleKey").assertIsDisplayed()
        composeTestRule.onNodeWithTag("$CONTACTS_OPTION_ROW_TAG_PREFIX$whatsappKey").assertIsDisplayed()
        composeTestRule.onNodeWithText(readOnlyLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("$CONTACTS_OPTION_ROW_TAG_PREFIX$whatsappKey").performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllContactAccounts)
        assertEquals(setOf(localKey, googleKey), selectedContactAccountKeys)
        composeTestRule.onNodeWithText(twoSelectedSubtitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()

        assertTrue(selectAllContactAccounts)
        assertTrue(selectedContactAccountKeys.isEmpty())

        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllContactAccounts)
        assertTrue(selectedContactAccountKeys.isEmpty())
        composeTestRule.onNodeWithText(noneSelectedSubtitle).assertIsDisplayed()
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
                    callLogsEnabled = false,
                    calendarEnabled = false,
                    selectAllCalendars = selectAllCalendars,
                    selectedCalendarKeys = selectedCalendarKeys,
                    availableCalendars = sampleCalendars,
                    onContactsEnabledChange = {},
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
