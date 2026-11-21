package io.github.hddq.restoid.ui.screens

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
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.buildCalendarSelectionKey
import io.github.hddq.restoid.model.buildContactAccountSelectionKey
import io.github.hddq.restoid.ui.shared.CALENDAR_OPTIONS_LOADING_TAG
import io.github.hddq.restoid.ui.shared.CALENDAR_OPTIONS_SELECT_ALL_TAG
import io.github.hddq.restoid.ui.shared.CALENDAR_OPTION_ROW_TAG_PREFIX
import io.github.hddq.restoid.ui.shared.CONTACTS_OPTIONS_LOADING_TAG
import io.github.hddq.restoid.ui.shared.CONTACTS_OPTIONS_SELECT_ALL_TAG
import io.github.hddq.restoid.ui.shared.CONTACTS_OPTION_ROW_TAG_PREFIX
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreDeviceDataConfigScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingContactsWithoutPermissionsOpensSheetAndTogglesAccountSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contactsTitle = context.getString(R.string.device_data_contacts)
        val noneSelectedSubtitle = context.getString(R.string.device_data_contacts_none_selected)
        val twoSelectedSubtitle = context.resources.getQuantityString(
            R.plurals.device_data_contacts_accounts_selected_count,
            2,
            2
        )
        val localCountLabel = context.resources.getQuantityString(R.plurals.device_data_contacts_count, 3, 3)

        val localKey = buildContactAccountSelectionKey(accountType = null, accountName = null)
        val googleKey = buildContactAccountSelectionKey("com.google", "alice@example.com")
        val whatsappKey = buildContactAccountSelectionKey("com.whatsapp", "WhatsApp")
        val sampleAccounts = listOf(
            ContactAccountInfo(
                selectionKey = localKey,
                accountName = "",
                accountType = "",
                displayName = context.getString(R.string.device_data_contacts_local_account),
                accountTypeLabel = null,
                isLocal = true,
                isReadOnly = false,
                itemCount = 3
            ),
            ContactAccountInfo(
                selectionKey = googleKey,
                accountName = "alice@example.com",
                accountType = "com.google",
                displayName = "alice@example.com",
                accountTypeLabel = "Google",
                isLocal = false,
                isReadOnly = false,
                itemCount = 5
            ),
            ContactAccountInfo(
                selectionKey = whatsappKey,
                accountName = "WhatsApp",
                accountType = "com.whatsapp",
                displayName = "WhatsApp",
                accountTypeLabel = "WhatsApp",
                isLocal = false,
                isReadOnly = true,
                itemCount = 2
            )
        )

        var loadCalled = false
        var permissionRequested = false
        var selectAllContactAccounts by mutableStateOf(true)
        var selectedContactAccountKeys by mutableStateOf(emptySet<String>())

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                RestoreDeviceDataConfigContent(
                    contactsMetadata = ContactsBackupMetadata(count = 10, size = 4096L, path = "/staging/contacts"),
                    callLogsMetadata = null,
                    calendarMetadata = null,
                    restoreContactsEnabled = false,
                    restoreCallLogsEnabled = false,
                    restoreCalendarEnabled = false,
                    hasContactsPermission = false,
                    selectAllContactAccounts = selectAllContactAccounts,
                    selectedContactAccountKeys = selectedContactAccountKeys,
                    availableContactAccounts = sampleAccounts,
                    onRequestContactsPermission = { permissionRequested = true },
                    onLoadSnapshotContactAccounts = { loadCalled = true },
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
                        val nextSelection = if (key in baseSelection) baseSelection - key else baseSelection + key
                        val allKeys = sampleAccounts.map { it.selectionKey }.toSet()
                        if (nextSelection.containsAll(allKeys)) {
                            selectAllContactAccounts = true
                            selectedContactAccountKeys = emptySet()
                        } else {
                            selectAllContactAccounts = false
                            selectedContactAccountKeys = nextSelection
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(contactsTitle).performClick()
        composeTestRule.waitForIdle()

        assertTrue(loadCalled)
        assertFalse(permissionRequested)
        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(localCountLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("$CONTACTS_OPTION_ROW_TAG_PREFIX$whatsappKey").performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllContactAccounts)
        assertEquals(setOf(localKey, googleKey), selectedContactAccountKeys)
        composeTestRule.onNodeWithText(twoSelectedSubtitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectAllContactAccounts)

        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()
        assertFalse(selectAllContactAccounts)
        assertTrue(selectedContactAccountKeys.isEmpty())
        composeTestRule.onNodeWithText(noneSelectedSubtitle).assertIsDisplayed()
    }

    @Test
    fun tappingCalendarWithoutPermissionsOpensSheetAndTogglesCalendarSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val calendarTitle = context.getString(R.string.device_data_calendar)
        val noneSelectedSubtitle = context.getString(R.string.device_data_calendar_none_selected)
        val oneSelectedSubtitle = context.resources.getQuantityString(
            R.plurals.device_data_calendars_selected_count,
            1,
            1
        )
        val personalEventsLabel = context.resources.getQuantityString(R.plurals.device_data_calendar_events_count, 4, 4)

        val personalKey = buildCalendarSelectionKey("com.google", "alice@example.com", "personal", "Personal")
        val workKey = buildCalendarSelectionKey("LOCAL", "Restoid", "work", "Work")
        val sampleCalendars = listOf(
            CalendarInfo(
                id = -1L,
                selectionKey = personalKey,
                name = "personal",
                displayName = "Personal",
                accountName = "alice@example.com",
                accountType = "com.google",
                color = 0xFF1E88E5.toInt(),
                timeZone = "UTC",
                isReadOnly = false,
                itemCount = 4
            ),
            CalendarInfo(
                id = -1L,
                selectionKey = workKey,
                name = "work",
                displayName = "Work",
                accountName = "Restoid",
                accountType = "LOCAL",
                color = 0xFF43A047.toInt(),
                timeZone = "UTC",
                isReadOnly = true,
                itemCount = 2
            )
        )

        var loadCalled = false
        var permissionRequested = false
        var selectAllCalendars by mutableStateOf(true)
        var selectedCalendarKeys by mutableStateOf(emptySet<String>())

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                RestoreDeviceDataConfigContent(
                    contactsMetadata = null,
                    callLogsMetadata = null,
                    calendarMetadata = CalendarBackupMetadata(
                        count = 6,
                        size = 4096L,
                        path = "/staging/calendar",
                        calendarCount = 2
                    ),
                    restoreContactsEnabled = false,
                    restoreCallLogsEnabled = false,
                    restoreCalendarEnabled = false,
                    hasCalendarPermission = false,
                    selectAllCalendars = selectAllCalendars,
                    selectedCalendarKeys = selectedCalendarKeys,
                    availableCalendars = sampleCalendars,
                    onRequestCalendarPermission = { permissionRequested = true },
                    onLoadSnapshotCalendars = { loadCalled = true },
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
                        val nextSelection = if (key in baseSelection) baseSelection - key else baseSelection + key
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

        composeTestRule.onNodeWithText(calendarTitle).performClick()
        composeTestRule.waitForIdle()

        assertTrue(loadCalled)
        assertFalse(permissionRequested)
        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(personalEventsLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("$CALENDAR_OPTION_ROW_TAG_PREFIX$workKey").performClick()
        composeTestRule.waitForIdle()

        assertFalse(selectAllCalendars)
        assertEquals(setOf(personalKey), selectedCalendarKeys)
        composeTestRule.onNodeWithText(oneSelectedSubtitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()
        assertTrue(selectAllCalendars)

        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).performClick()
        composeTestRule.waitForIdle()
        assertFalse(selectAllCalendars)
        assertTrue(selectedCalendarKeys.isEmpty())
        composeTestRule.onNodeWithText(noneSelectedSubtitle).assertIsDisplayed()
    }

    @Test
    fun contactsSheetRendersLoadingErrorRetryAndSnapshotEmptyStates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contactsTitle = context.getString(R.string.device_data_contacts)
        val retryLabel = context.getString(R.string.action_retry)
        val contactsEmpty = context.getString(R.string.device_data_contacts_accounts_snapshot_empty)

        var isLoadingContacts by mutableStateOf(true)
        var contactsError by mutableStateOf<String?>(null)
        var contactsRetryCount = 0

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                RestoreDeviceDataConfigContent(
                    contactsMetadata = ContactsBackupMetadata(count = 0, size = 64L, path = "/staging/contacts"),
                    callLogsMetadata = null,
                    calendarMetadata = null,
                    restoreContactsEnabled = true,
                    restoreCallLogsEnabled = false,
                    restoreCalendarEnabled = false,
                    isLoadingContactAccounts = isLoadingContacts,
                    contactAccountsError = contactsError,
                    onRetrySnapshotContactAccounts = { contactsRetryCount++ }
                )
            }
        }

        // Contacts loading -> error + retry -> snapshot empty
        composeTestRule.onNodeWithText(contactsTitle).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_LOADING_TAG).assertIsDisplayed()

        isLoadingContacts = false
        contactsError = "Failed to inspect contacts.json"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Failed to inspect contacts.json").assertIsDisplayed()
        composeTestRule.onNodeWithText(retryLabel).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, contactsRetryCount)

        contactsError = null
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(contactsEmpty).assertIsDisplayed()
    }

    @Test
    fun calendarSheetRendersLoadingErrorRetryAndSnapshotEmptyStates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val calendarTitle = context.getString(R.string.device_data_calendar)
        val retryLabel = context.getString(R.string.action_retry)
        val calendarEmpty = context.getString(R.string.device_data_calendar_snapshot_empty)

        var isLoadingCalendars by mutableStateOf(true)
        var calendarsError by mutableStateOf<String?>(null)
        var calendarsRetryCount = 0

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                RestoreDeviceDataConfigContent(
                    contactsMetadata = null,
                    callLogsMetadata = null,
                    calendarMetadata = CalendarBackupMetadata(count = 0, size = 64L, path = "/staging/calendar"),
                    restoreContactsEnabled = false,
                    restoreCallLogsEnabled = false,
                    restoreCalendarEnabled = true,
                    isLoadingCalendars = isLoadingCalendars,
                    calendarsError = calendarsError,
                    onRetrySnapshotCalendars = { calendarsRetryCount++ }
                )
            }
        }

        composeTestRule.onNodeWithText(calendarTitle).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_LOADING_TAG).assertIsDisplayed()

        isLoadingCalendars = false
        calendarsError = "Failed to inspect calendar.json"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Failed to inspect calendar.json").assertIsDisplayed()
        composeTestRule.onNodeWithText(retryLabel).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, calendarsRetryCount)

        calendarsError = null
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(calendarEmpty).assertIsDisplayed()
    }

    @Test
    fun unavailableSnapshotItemsAreDisabledAndDoNotOpenSheets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contactsTitle = context.getString(R.string.device_data_contacts)
        val calendarTitle = context.getString(R.string.device_data_calendar)

        var contactsLoadCalled = false
        var calendarLoadCalled = false

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                RestoreDeviceDataConfigContent(
                    contactsMetadata = null,
                    callLogsMetadata = null,
                    calendarMetadata = null,
                    restoreContactsEnabled = false,
                    restoreCallLogsEnabled = false,
                    restoreCalendarEnabled = false,
                    onLoadSnapshotContactAccounts = { contactsLoadCalled = true },
                    onLoadSnapshotCalendars = { calendarLoadCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText(contactsTitle).performClick()
        composeTestRule.onNodeWithText(calendarTitle).performClick()
        composeTestRule.waitForIdle()

        assertFalse(contactsLoadCalled)
        assertFalse(calendarLoadCalled)
        composeTestRule.onNodeWithTag(CONTACTS_OPTIONS_SELECT_ALL_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(CALENDAR_OPTIONS_SELECT_ALL_TAG).assertDoesNotExist()
    }
}
