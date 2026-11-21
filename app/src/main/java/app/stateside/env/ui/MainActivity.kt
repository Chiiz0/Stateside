package app.stateside.env.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stateside.env.ui.theme.StatesideTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.refreshSystemState()
            }

            val requestRuntimePermissions = {
                val perms = buildList {
                    add(Manifest.permission.READ_PHONE_STATE)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                    if (Build.VERSION.SDK_INT >= 37) {
                        add("android.permission.ACCESS_LOCAL_NETWORK")
                    }
                }
                permissionLauncher.launch(perms.toTypedArray())
            }

            StatesideTheme(
                themeMode = state.userSettings.themeMode,
                dynamicColor = state.userSettings.useDynamicColor
            ) {
                MainScreen(
                    state = state,
                    onRequestPermissions = requestRuntimePermissions,
                    onRefresh = viewModel::refreshSystemState,
                    onApplyStateside = { viewModel.applyStatesideEnv(null) },
                    onConnectManualPortAndApply = viewModel::connectManualPortAndApply,
                    onSafeLaunchApp = viewModel::safeLaunchTargetApp,
                    onRestoreDefault = viewModel::restoreToDomesticDefault,
                    onSelectCarrier = viewModel::selectCarrier,
                    onApplyCustomCarrierLabel = viewModel::applyCustomCarrierLabel,
                    onUpdateSlotDisplayOverride = viewModel::updateSlotDisplayOverride,
                    onToggleAutoBoot = viewModel::setAutoRestoreOnBoot,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onToggleDynamicColor = viewModel::setUseDynamicColor,
                    onSaveManualPort = viewModel::saveManualConnectPort,
                    onResetPairingState = viewModel::resetAdbPairingState,
                    onToggleCoreSuiteApp = viewModel::toggleCoreSuiteApp,
                    onOpenAppPicker = viewModel::loadSelectableUserAppsIfNeeded,
                    onToggleExtraPkg = viewModel::toggleExtraEnglishPackage,
                    onPinShortcutForApp = viewModel::requestPinShortcutForApp,
                    onBuildDiagnostics = viewModel::buildDiagnosticSummary,
                    onNotifyBanner = viewModel::notifyBanner,
                    onClearMessages = viewModel::clearMessages
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState()
    }
}
