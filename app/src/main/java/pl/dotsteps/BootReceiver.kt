package pl.dotsteps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StepStore.init(context)
        if (StepStore.isStopped()) return          // zakończona ręcznie – wstaje dopiero po otwarciu aplikacji
        if (!Perms.hasActivity(context)) return
        try {
            StepService.start(context)
        } catch (_: Exception) {
        }
    }
}
