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
                val extraPort = intent.getIntExtra(AdbPairingService.EXTRA_PORT, 0)
                val port = if (extraPort in 1..65535) extraPort else AdbPairingService.latestDetectedPort
                val host = intent.getStringExtra(AdbPairingService.EXTRA_HOST)
                    ?.takeIf { it.isNotBlank() }
                    ?: AdbPairingService.latestDetectedHost
                AdbPairingService.submitCodeFromUi(context, port, code, host)
            }
            AdbPairingService.ACTION_STOP -> {
                AdbPairingService.stopWatching(context)
            }
        }
    }
}
