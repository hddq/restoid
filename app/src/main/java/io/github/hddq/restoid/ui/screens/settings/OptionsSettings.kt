package io.github.hddq.restoid.ui.screens.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.screens.settings.components.AppUnlockOnStartRow
import io.github.hddq.restoid.ui.screens.settings.components.DeviceHostnameRow
import io.github.hddq.restoid.ui.screens.settings.components.SettingsToggleRow
import io.github.hddq.restoid.ui.screens.settings.dialogs.EditHostnameDialog
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun OptionsSettings(viewModel: SettingsViewModel) {
    val requireAppUnlock by viewModel.requireAppUnlock.collectAsStateWithLifecycle()
    val allowExternalIntents by viewModel.allowExternalIntents.collectAsStateWithLifecycle()
    val deviceHostname by viewModel.deviceHostname.collectAsStateWithLifecycle()
    val customDeviceHostname by viewModel.customDeviceHostname.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEditHostnameDialog by rememberSaveable { mutableStateOf(false) }

    if (showEditHostnameDialog) {
        EditHostnameDialog(
            currentHostname = customDeviceHostname ?: deviceHostname,
            defaultHostname = viewModel.defaultDeviceHostname,
            onConfirm = { newHostname ->
                viewModel.onDeviceHostnameChanged(newHostname)
            },
            onDismiss = { showEditHostnameDialog = false }
        )
    }

    OptionsSettingsContent(
        requireAppUnlock = requireAppUnlock,
        allowExternalIntents = allowExternalIntents,
        deviceHostname = deviceHostname,
        onRequireAppUnlockChange = { required ->
            if (required) {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    val canAuthenticate = BiometricManager.from(activity).canAuthenticate(authenticators)

                    if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(activity.getString(R.string.app_unlock_prompt_title))
                            .setSubtitle(activity.getString(R.string.app_unlock_prompt_subtitle))
                            .setAllowedAuthenticators(authenticators)
                            .build()

                        val biometricPrompt = BiometricPrompt(
                            activity,
                            ContextCompat.getMainExecutor(activity),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    viewModel.onRequireAppUnlockChanged(true)
                                }
                            }
                        )
                        biometricPrompt.authenticate(promptInfo)
                    } else {
                        viewModel.onRequireAppUnlockChanged(true)
                    }
                } else {
                    viewModel.onRequireAppUnlockChanged(true)
                }
            } else {
                viewModel.onRequireAppUnlockChanged(false)
            }
        },
        onAllowExternalIntentsChange = viewModel::onAllowExternalIntentsChanged,
        onEditHostnameClick = { showEditHostnameDialog = true }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OptionsSettingsContent(
    requireAppUnlock: Boolean,
    allowExternalIntents: Boolean,
    deviceHostname: String,
    onRequireAppUnlockChange: (Boolean) -> Unit = {},
    onAllowExternalIntentsChange: (Boolean) -> Unit = {},
    onEditHostnameClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.options_title))
        ExpressiveCardGroup {
            AppUnlockOnStartRow(
                enabled = requireAppUnlock,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                onCheckedChange = onRequireAppUnlockChange
            )
            SettingsToggleRow(
                title = stringResource(R.string.allow_external_intents),
                icon = Icons.Outlined.Bolt,
                enabled = allowExternalIntents,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                onCheckedChange = onAllowExternalIntentsChange
            )
            DeviceHostnameRow(
                hostname = deviceHostname,
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                onClick = onEditHostnameClick
            )
        }
    }
}
