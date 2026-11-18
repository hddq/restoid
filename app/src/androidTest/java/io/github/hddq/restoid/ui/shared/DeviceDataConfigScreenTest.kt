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
import io.github.hddq.restoid.ui.theme.RestoidTheme
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
}
