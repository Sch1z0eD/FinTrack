package com.findev.fintrack.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The broadcast receiver Android talks to; it just points at [NextPaymentWidget]. */
class NextPaymentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPaymentWidget()
}
