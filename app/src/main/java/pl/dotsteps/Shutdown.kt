package pl.dotsteps

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process

object Shutdown {
    /** Zapisuje wynik, zatrzymuje usługę, zamyka UI i zabija proces. */
    fun quit(ctx: Context) {
        StepStore.setStopped(true)
        var c = ctx
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val activity = c as? Activity

        if (StepService.running) {
            // usługa sama: flush czujnika -> zapis -> stopForeground -> kill procesu
            ctx.startService(Intent(ctx, StepService::class.java).setAction(StepService.ACTION_QUIT))
            activity?.finishAndRemoveTask()
        } else {
            activity?.finishAndRemoveTask()
            Process.killProcess(Process.myPid())
        }
    }
}
