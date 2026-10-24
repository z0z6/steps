package pl.dotsteps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Perms.hasActivity(context)) return
        try {
            StepService.start(context)
        } catch (_: Exception) {
            // system może zablokować start usługi w tle – usługa wystartuje po otwarciu aplikacji
        }
    }
}
