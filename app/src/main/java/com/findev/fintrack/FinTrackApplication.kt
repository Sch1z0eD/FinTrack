package com.findev.fintrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.findev.fintrack.notification.LoanReminderScheduler
import com.findev.fintrack.notification.MeterReminderScheduler
import com.findev.fintrack.notification.PaymentReminderScheduler
import com.findev.fintrack.update.UpdateChecks
import com.findev.fintrack.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class FinTrackApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var reminderScheduler: PaymentReminderScheduler

    @Inject
    lateinit var meterReminderScheduler: MeterReminderScheduler

    @Inject
    lateinit var loanReminderScheduler: LoanReminderScheduler

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var updateChecks: UpdateChecks

    /** Lives as long as the process. The alarms it sets outlive it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lets WorkManager construct the Hilt-injected MeterReminderWorker. Providing this
    // disables the default initializer (removed in the manifest), so WorkManager comes up
    // on demand with this factory instead.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderScheduler.createChannel()
        reminderScheduler.observePayments(scope)

        meterReminderScheduler.createChannel()
        meterReminderScheduler.schedule()

        loanReminderScheduler.createChannel()
        loanReminderScheduler.observeLoans(scope)

        updateChecks.setUp()

        widgetUpdater.observe(scope)
    }
}
