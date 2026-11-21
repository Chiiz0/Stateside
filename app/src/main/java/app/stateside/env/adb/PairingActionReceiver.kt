package app.stateside.env.adb

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PairingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            AdbPairingService.ACTION_SUBMIT_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val code = results?.getCharSequence(AdbPairingService.KEY_REMOTE_PAIRING_CODE)
                    ?.toString()
                    .orEmpty()
                val port = intent.getIntExtra(
                    AdbPairingService.EXTRA_PORT,
                    AdbPairingService.latestDetectedPort
                )
                AdbPairingService.submitCodeFromUi(context, port, code)
            }
            AdbPairingService.ACTION_STOP -> {
                AdbPairingService.stopWatching(context)
            }
        }
    }
}
