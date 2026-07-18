package com.findev.fintrack.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alarms do not survive a reboot, so they are rebuilt after one.
 *
 * Without this a phone restarted on the 16th would silently drop every reminder due on
 * the 17th, and the failure would look exactly like the feature not working.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: PaymentReminderScheduler

    @Inject
    lateinit var loanScheduler: LoanReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.rescheduleAll()
                loanScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
