package io.github.hddq.restoid.ui.screens.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.screens.settings.components.AppUnlockOnStartRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun OptionsSettings(viewModel: SettingsViewModel) {
    val requireAppUnlock by viewModel.requireAppUnlock.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.options_title))
        ExpressiveSegmentedSurface(index = 0, count = 1) {
            AppUnlockOnStartRow(
                enabled = requireAppUnlock,
                onCheckedChange = { required ->
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
                }
            )
        }
    }
}
