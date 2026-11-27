package io.github.hddq.restoid.screenshots

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hddq.restoid.AdaptiveNavigationLayout
import io.github.hddq.restoid.DestinationScaffold
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidShortNavigationBar
import io.github.hddq.restoid.Screen
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.presets.PresetsContent
import io.github.hddq.restoid.ui.runtasks.RunTasksContent
import io.github.hddq.restoid.ui.schedules.AddEditScheduleContent
import io.github.hddq.restoid.ui.screens.HomeScreen
import io.github.hddq.restoid.ui.screens.SnapshotDetailsContent
import io.github.hddq.restoid.ui.screens.settings.AboutSettings
import io.github.hddq.restoid.ui.screens.settings.AddRepositoryDialog
import io.github.hddq.restoid.ui.screens.settings.DependencySettingsContent
import io.github.hddq.restoid.ui.screens.settings.OptionsSettingsContent
import io.github.hddq.restoid.ui.screens.settings.RepositorySettingsContent
import io.github.hddq.restoid.ui.screens.settings.SystemSettingsCard
import io.github.hddq.restoid.ui.components.AppSortMenuButton
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.BackupConfigScreen
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.ProgressScreenContent
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val testContext
        get() = InstrumentationRegistry.getInstrumentation().context

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var previousLocale: Locale

    private val enUsTargetContext: Context
        get() {
            val config = Configuration(targetContext.resources.configuration).apply {
                setLocale(Locale.US)
            }
            return targetContext.createConfigurationContext(config)
        }

    @Before
    fun configureEdgeToEdgeDark() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
            }
        }
    }

    @After
    fun restoreLocale() {
        if (::previousLocale.isInitialized) {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun capture1_home() {
        val (resticState, _) = ScreenshotFixtures.bundledBinaryStates(targetContext)
        val state = ScreenshotFixtures.homeUiState(testContext, resticState)
        setScreenshotContent {
            TopLevelScreenshotHost(
                selectedScreen = Screen.Home,
                titleRes = R.string.home_title,
                titleOverride = "Restoid",
                isHome = true,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = stringResource(R.string.screen_operation_logs)
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.screen_tasks)) },
                        icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                        onClick = {},
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            ) {
                HomeScreen(
                    onSnapshotClick = {},
                    uiState = state,
                    onRefresh = {},
                    onPasswordEntered = { _, _ -> },
                    onSftpPasswordEntered = { _, _, _ -> },
                    onRestCredentialsEntered = { _, _, _ -> },
                    onS3CredentialsEntered = { _, _, _ -> },
                    onRetryRepositoryPasswordEntry = {},
                    onRetrySftpPasswordEntry = {},
                    onRetryRestCredentialsEntry = {},
                    onRetryS3CredentialsEntry = {},
                    onDismissPasswordDialog = {},
                    onDismissSftpPasswordDialog = {},
                    onDismissRestCredentialsDialog = {},
                    onDismissS3CredentialsDialog = {}
                )
            }
        }
        saveScreenshot("1.png")
    }

    @Test
    fun capture2_snapshotDetails() {
        val (snapshot, metadata, backupDetails) = ScreenshotFixtures.snapshotDetailsData(testContext)
        setScreenshotContent {
            DestinationScaffold(
                titleRes = R.string.topbar_snapshot_details,
                isTopLevel = false,
                onBack = {},
                actions = {
                    AppSortMenuButton(
                        sortOrder = AppSortOrder.NAME_ASCENDING,
                        onSortOrderChange = {}
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_forget_snapshot),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        shape = RoundedCornerShape(20.dp),
                        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        text = { Text(stringResource(R.string.fab_restore)) }
                    )
                }
            ) {
                SnapshotDetailsContent(
                    snapshot = snapshot,
                    metadata = metadata,
                    backupDetails = backupDetails,
                    availableTags = ScreenshotFixtures.tags
                )
            }
        }
        saveScreenshot("2.png")
    }

    @Test
    fun capture3_runTasks() {
        val state = ScreenshotFixtures.runTasksUiState(testContext)
        setScreenshotContent {
            DestinationScaffold(
                titleRes = R.string.topbar_tasks,
                isTopLevel = false,
                onBack = {},
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = stringResource(R.string.action_save_as_preset)
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.cd_help)
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        shape = RoundedCornerShape(20.dp),
                        icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                        text = { Text(stringResource(R.string.fab_run_tasks)) }
                    )
                }
            ) {
                RunTasksContent(
                    uiState = state,
                    hasRoot = true
                )
            }
        }
        saveScreenshot("3.png")
    }

    @Test
    fun capture4_backupConfigWithTypesSheet() {
        val apps = ScreenshotFixtures.sampleApps(testContext)
        setScreenshotContent {
            DestinationScaffold(
                titleRes = R.string.topbar_backup_config,
                isTopLevel = false,
                onBack = {},
                actions = {
                    AppSortMenuButton(
                        sortOrder = AppSortOrder.NAME_ASCENDING,
                        onSortOrderChange = {}
                    )
                }
            ) {
                BackupConfigScreen(
                    isLoadingApps = false,
                    apps = apps,
                    appBackupTypes = emptyMap(),
                    backupTypes = BackupTypes(),
                    onRefreshApps = {},
                    onToggleAllApps = {},
                    onToggleAppSelection = {},
                    onSetSelectedAppsBackupTypes = {},
                    onSetAppBackupTypes = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(enUsTargetContext.getString(R.string.toggle_all)).performClick()
        saveScreenshot("4.png", settleMillis = 500L)
    }

    @Test
    fun capture5_operationProgress() {
        composeRule.mainClock.autoAdvance = false
        try {
            setScreenshotContent {
                DestinationScaffold(
                    titleRes = R.string.topbar_operation_progress,
                    isTopLevel = false,
                    onBack = {}
                ) {
                    ProgressScreenContent(
                        progress = ScreenshotFixtures.operationProgress(),
                        operationType = stringResource(R.string.operation_run_tasks),
                        onDone = {}
                    )
                }
            }
            composeRule.mainClock.advanceTimeBy(450L)
            saveScreenshot("5.png", waitForComposeIdle = false, settleMillis = 350L)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun capture6_presets() {
        val state = ScreenshotFixtures.presetsUiState()
        setScreenshotContent {
            TopLevelScreenshotHost(
                selectedScreen = Screen.Presets,
                titleRes = Screen.Presets.titleRes
            ) {
                PresetsContent(
                    uiState = state,
                    hasRoot = true
                )
            }
        }
        saveScreenshot("6.png")
    }

    @Test
    fun capture7_editSchedule() {
        val (state, conditionStatus) = ScreenshotFixtures.editScheduleUiState()
        setScreenshotContent {
            DestinationScaffold(
                titleRes = R.string.topbar_edit_schedule,
                isTopLevel = false,
                onBack = {},
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(R.string.action_run_now),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        shape = RoundedCornerShape(20.dp),
                        icon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_save)) }
                    )
                }
            ) {
                AddEditScheduleContent(
                    state = state,
                    conditionStatus = conditionStatus,
                    hasRoot = true,
                    onCreatePreset = {},
                    onEditPreset = {}
                )
            }
        }
        saveScreenshot("7.png")
    }

    @Test
    fun capture8_settings() {
        val (resticState, rcloneState) = ScreenshotFixtures.bundledBinaryStates(targetContext)
        setScreenshotContent {
            SettingsScreenshotContent(
                resticState = resticState,
                rcloneState = rcloneState,
                showAddRepositoryDialog = false
            )
        }
        saveScreenshot("8.png")
    }

    @Test
    fun capture9_addRepositoryDialog() {
        val (resticState, rcloneState) = ScreenshotFixtures.bundledBinaryStates(targetContext)
        setScreenshotContent {
            SettingsScreenshotContent(
                resticState = resticState,
                rcloneState = rcloneState,
                showAddRepositoryDialog = true
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(enUsTargetContext.getString(R.string.repo_backend_local)).performClick()
        saveScreenshot("9.png", settleMillis = 450L)
    }

    @Composable
    private fun SettingsScreenshotContent(
        resticState: ResticState,
        rcloneState: ResticState,
        showAddRepositoryDialog: Boolean
    ) {
        TopLevelScreenshotHost(
            selectedScreen = Screen.Settings,
            titleRes = Screen.Settings.titleRes
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SystemSettingsCard(
                        hasRoot = true,
                        summaryText = stringResource(R.string.permissions_summary_root_all_ready)
                    )
                }
                item {
                    DependencySettingsContent(
                        resticState = resticState,
                        rcloneState = rcloneState
                    )
                }
                item {
                    RepositorySettingsContent(
                        resticState = resticState,
                        repositories = ScreenshotFixtures.repositories,
                        selectedRepositoryKey = ScreenshotFixtures.PRIMARY_REPO_KEY,
                        repositoryKey = { it.path }
                    )
                }
                item {
                    OptionsSettingsContent(
                        requireAppUnlock = false,
                        allowExternalIntents = true,
                        deviceHostname = ScreenshotFixtures.HOSTNAME
                    )
                }
                item {
                    AboutSettings(onNavigateToLicenses = {})
                }
            }

            if (showAddRepositoryDialog) {
                AddRepositoryDialog(
                    uiState = ScreenshotFixtures.addRepoDialogState(),
                    hasRcloneConfig = true,
                    rcloneRemotes = listOf("nextcloud", "b2"),
                    onDismiss = {},
                    onBackendTypeChange = {},
                    onPathChange = {},
                    onSftpHostChange = {},
                    onSftpPortChange = {},
                    onSftpUsernameChange = {},
                    onSftpPathChange = {},
                    onPasswordChange = {},
                    onSftpPasswordChange = {},
                    onSftpKeyChange = {},
                    onSftpKeyImportError = {},
                    onSftpKeyPassphraseChange = {},
                    onSftpAuthMethodChange = {},
                    onS3AccessKeyIdChange = {},
                    onS3SecretAccessKeyChange = {},
                    onRestUsernameChange = {},
                    onRestPasswordChange = {},
                    onEnvironmentVariablesChange = {},
                    onSavePasswordChange = {},
                    onConfirm = {},
                    onSelectPath = {},
                    hasStorageAccess = true
                )
            }
        }
    }

    @Composable
    private fun TopLevelScreenshotHost(
        selectedScreen: Screen,
        titleRes: Int,
        titleOverride: String? = null,
        isHome: Boolean = false,
        actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
        floatingActionButton: @Composable () -> Unit = {},
        content: @Composable () -> Unit
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DestinationScaffold(
                titleRes = titleRes,
                isTopLevel = true,
                reserveBottomNavSpace = true,
                isHome = isHome,
                titleOverride = titleOverride,
                onBack = {},
                actions = actions,
                floatingActionButton = floatingActionButton,
                content = content
            )
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RestoidShortNavigationBar(
                    layout = AdaptiveNavigationLayout.BottomBarCompact,
                    selectedScreen = selectedScreen,
                    interactionEnabled = true,
                    onSelectScreen = {}
                )
            }
        }
    }

    private fun setScreenshotContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val enUsConfig = remember(baseConfig) {
                Configuration(baseConfig).apply {
                    setLocale(Locale.US)
                }
            }
            val enUsContext = remember(baseContext, enUsConfig) {
                baseContext.createConfigurationContext(enUsConfig)
            }
            CompositionLocalProvider(
                LocalConfiguration provides enUsConfig,
                LocalContext provides enUsContext
            ) {
                RestoidTheme(darkTheme = true, dynamicColor = false) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun saveScreenshot(
        fileName: String,
        waitForComposeIdle: Boolean = true,
        settleMillis: Long = 250L
    ) {
        if (waitForComposeIdle) {
            composeRule.waitForIdle()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        waitForRenderedFrames()
        if (settleMillis > 0L) {
            Thread.sleep(settleMillis)
        }

        var bitmap: Bitmap? = null
        for (attempt in 0 until 10) {
            val candidate = instrumentation.uiAutomation.takeScreenshot()
                ?: error("UiAutomation.takeScreenshot() returned null for $fileName")
            if (hasVisualContent(candidate)) {
                bitmap = candidate
                break
            }
            candidate.recycle()
            if (attempt < 9) {
                waitForRenderedFrames()
                Thread.sleep(300L)
            }
        }

        val finalBitmap = bitmap
            ?: error("Captured screenshot for $fileName remained blank after retries")
        val outDir = File(targetContext.getExternalFilesDir(null), "screenshots").apply {
            mkdirs()
        }
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { stream ->
            check(finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Failed to write screenshot to ${outFile.absolutePath}"
            }
        }
        finalBitmap.recycle()
    }

    private fun waitForRenderedFrames(frameCount: Int = 2) {
        repeat(frameCount) {
            val latch = CountDownLatch(1)
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.window.decorView.post {
                    Choreographer.getInstance().postFrameCallback {
                        latch.countDown()
                    }
                }
            }
            latch.await(2, TimeUnit.SECONDS)
        }
    }

    private fun hasVisualContent(bitmap: Bitmap): Boolean {
        val cols = 6
        val rows = 8
        var firstPixel: Int? = null
        for (row in 0 until rows) {
            val y = ((0.15f + 0.70f * row / (rows - 1)) * bitmap.height).toInt()
                .coerceIn(0, bitmap.height - 1)
            for (col in 0 until cols) {
                val x = ((0.15f + 0.70f * col / (cols - 1)) * bitmap.width).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val pixel = bitmap.getPixel(x, y)
                if (firstPixel == null) {
                    firstPixel = pixel
                } else if (pixel != firstPixel) {
                    return true
                }
            }
        }
        return false
    }
}
