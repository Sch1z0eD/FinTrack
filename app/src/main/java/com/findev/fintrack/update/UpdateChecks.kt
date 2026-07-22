package com.findev.fintrack.update

/**
 * Flavor seam for the background update check.
 *
 * The github flavor binds this to the scheduler that enqueues the daily GitHub release check;
 * the rustore flavor binds a no-op, because a RuStore build must not reach out for updates at
 * all. [FinTrackApplication] calls [setUp] once on startup without knowing which is behind it.
 */
interface UpdateChecks {
    fun setUp()
}
