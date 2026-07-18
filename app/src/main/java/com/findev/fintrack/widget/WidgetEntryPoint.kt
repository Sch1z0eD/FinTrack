package com.findev.fintrack.widget

import android.content.Context
import com.findev.fintrack.data.NextPaymentRepository
import com.findev.fintrack.data.ObligationsRepository
import com.findev.fintrack.data.OverviewRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * A Glance widget is not a Hilt-injected component, so it reaches repositories through this
 * entry point instead of constructor injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun overviewRepository(): OverviewRepository
    fun nextPaymentRepository(): NextPaymentRepository
    fun obligationsRepository(): ObligationsRepository
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
