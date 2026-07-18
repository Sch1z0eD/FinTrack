package com.findev.fintrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.findev.fintrack.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val SHORT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy")

/** "15.06.27": the year still matters on a loan, but a schedule row has no space for it. */
fun shortDate(date: LocalDate): String = date.format(SHORT_FORMATTER)

/** "Сегодня" / "Вчера" / "12.07.2026". */
@Composable
fun dateLabel(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> stringResource(R.string.date_today)
        today - 1 -> stringResource(R.string.date_yesterday)
        else -> LocalDate.ofEpochDay(epochDay).format(DATE_FORMATTER)
    }
}
