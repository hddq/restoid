package io.github.hddq.restoid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelTabHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tabOrder = mapOf(
        Screen.Home.route to 0,
        Screen.Presets.route to 1,
        Screen.Schedules.route to 2,
        Screen.Settings.route to 3
    )

    @Test
    fun fastSwitchFromSettingsToHome_tappingFabAreaMidTransitionHitsHomeNotSettings() {
        var selectedTab by mutableStateOf<Screen>(Screen.Home)
        var homeFabClicks = 0
        var settingsSurfaceClicks = 0
        var settingsResumeCount = 0

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                TopLevelTabHost(
                    selectedTab = selectedTab,
                    tabOrder = tabOrder,
                    isParentExiting = false
                ) { tab, isExiting ->
                    when (tab) {
                        Screen.Home -> {
                            DestinationScaffold(
                                titleRes = R.string.home_title,
                                isTopLevel = true,
                                isHome = true,
                                isExiting = isExiting,
                                onBack = {},
                                floatingActionButton = {
                                    ExtendedFloatingActionButton(
                                        modifier = Modifier.testTag("home_fab"),
                                        onClick = { homeFabClicks += 1 },
                                        icon = {},
                                        text = { Text("Run") }
                                    )
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        Screen.Settings -> {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) {
                                        settingsResumeCount += 1
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                            }
                            DestinationScaffold(
                                titleRes = R.string.screen_settings,
                                isTopLevel = true,
                                isExiting = isExiting,
                                onBack = {}
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("settings_surface")
                                        .clickable { settingsSurfaceClicks += 1 }
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val fabBounds = composeTestRule.onNodeWithTag("home_fab").getUnclippedBoundsInRoot()
        val fabCenterPx = with(composeTestRule.density) {
            Offset(
                x = (fabBounds.left + (fabBounds.right - fabBounds.left) / 2f).toPx(),
                y = (fabBounds.top + (fabBounds.bottom - fabBounds.top) / 2f).toPx()
            )
        }

        // Switch to Settings and let it settle.
        composeTestRule.runOnIdle {
            selectedTab = Screen.Settings
        }
        composeTestRule.waitForIdle()
        assertEquals(1, settingsResumeCount)

        // Switch quickly from Settings to Home while pausing the animation clock mid-transition.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle {
            selectedTab = Screen.Home
        }
        composeTestRule.mainClock.advanceTimeBy(120L)

        // Both Home and exiting Settings are present in the transition tree at t = 120ms.
        composeTestRule.onAllNodesWithTag("settings_surface").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("home_fab").assertCountEquals(1)

        // Tap the settled Home FAB coordinate during the transition.
        composeTestRule.onRoot().performTouchInput {
            click(fabCenterPx)
        }

        assertEquals(1, homeFabClicks)
        assertEquals(0, settingsSurfaceClicks)

        // Finish transition and verify exiting Settings is removed cleanly.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("settings_surface").assertCountEquals(0)
        assertTrue(homeFabClicks == 1)
    }

    @Test
    fun reselectSettingsBeforeExitCompletes_tappingOverlapHitsSettingsNotHome() {
        var selectedTab by mutableStateOf<Screen>(Screen.Home)
        var homeFabClicks = 0
        var settingsSurfaceClicks = 0

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                TopLevelTabHost(
                    selectedTab = selectedTab,
                    tabOrder = tabOrder,
                    isParentExiting = false
                ) { tab, isExiting ->
                    when (tab) {
                        Screen.Home -> {
                            DestinationScaffold(
                                titleRes = R.string.home_title,
                                isTopLevel = true,
                                isHome = true,
                                isExiting = isExiting,
                                onBack = {},
                                floatingActionButton = {
                                    ExtendedFloatingActionButton(
                                        modifier = Modifier.testTag("home_fab"),
                                        onClick = { homeFabClicks += 1 },
                                        icon = {},
                                        text = { Text("Run") }
                                    )
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        Screen.Settings -> {
                            DestinationScaffold(
                                titleRes = R.string.screen_settings,
                                isTopLevel = true,
                                isExiting = isExiting,
                                onBack = {}
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("settings_surface")
                                        .clickable { settingsSurfaceClicks += 1 }
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val fabBounds = composeTestRule.onNodeWithTag("home_fab").getUnclippedBoundsInRoot()
        val fabCenterPx = with(composeTestRule.density) {
            Offset(
                x = (fabBounds.left + (fabBounds.right - fabBounds.left) / 2f).toPx(),
                y = (fabBounds.top + (fabBounds.bottom - fabBounds.top) / 2f).toPx()
            )
        }

        // Start on settled Settings.
        composeTestRule.runOnIdle {
            selectedTab = Screen.Settings
        }
        composeTestRule.waitForIdle()

        // Switch Settings -> Home, then immediately reselect Settings before Settings exits composition.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle {
            selectedTab = Screen.Home
        }
        composeTestRule.mainClock.advanceTimeBy(48L)
        composeTestRule.runOnIdle {
            selectedTab = Screen.Settings
        }
        composeTestRule.mainClock.advanceTimeBy(48L)

        composeTestRule.onAllNodesWithTag("settings_surface").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("home_fab").assertCountEquals(1)

        // Reselected Settings must sit above outgoing Home and handle the tap.
        composeTestRule.onRoot().performTouchInput {
            click(fabCenterPx)
        }

        assertEquals(1, settingsSurfaceClicks)
        assertEquals(0, homeFabClicks)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun tabSwitchFromSchedulesToSettings_demotesSchedulesBeforeSettingsOnResume() {
        var selectedTab by mutableStateOf<Screen>(Screen.Schedules)
        var schedulesLifecycleOwner: LifecycleOwner? = null
        var schedulesStateOnSettingsResume: Lifecycle.State? = null

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                TopLevelTabHost(
                    selectedTab = selectedTab,
                    tabOrder = tabOrder,
                    isParentExiting = false
                ) { tab, isExiting ->
                    when (tab) {
                        Screen.Schedules -> {
                            schedulesLifecycleOwner = LocalLifecycleOwner.current
                            DestinationScaffold(
                                titleRes = R.string.topbar_schedules,
                                isTopLevel = true,
                                isExiting = isExiting,
                                onBack = {}
                            ) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        Screen.Settings -> {
                            val settingsLifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(settingsLifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) {
                                        schedulesStateOnSettingsResume =
                                            schedulesLifecycleOwner?.lifecycle?.currentState
                                    }
                                }
                                settingsLifecycleOwner.lifecycle.addObserver(observer)
                                onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
                            }
                            DestinationScaffold(
                                titleRes = R.string.screen_settings,
                                isTopLevel = true,
                                isExiting = isExiting,
                                onBack = {}
                            ) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(Lifecycle.State.RESUMED, schedulesLifecycleOwner?.lifecycle?.currentState)

        composeTestRule.runOnIdle {
            selectedTab = Screen.Settings
        }
        composeTestRule.waitForIdle()

        assertNotEquals(Lifecycle.State.RESUMED, schedulesStateOnSettingsResume)
        assertEquals(Lifecycle.State.STARTED, schedulesStateOnSettingsResume)
    }
}
