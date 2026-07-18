package com.findev.fintrack.ui

import kotlin.math.absoluteValue

/** Non-breaking space, so a thousands group never wraps to the next line. */
private const val GROUP_SEPARATOR = ' '

/**
 * Formats kopecks as "1 234,56" — integer math only, never Double.
 * The currency symbol is appended by the caller via R.string.money_with_currency.
 */
fun formatMinor(amountMinor: Long): String {
    val sign = if (amountMinor < 0) "-" else ""
    val absolute = amountMinor.absoluteValue
    val rubles = absolute / 100
    val kopecks = absolute % 100
    return "$sign${groupThousands(rubles)},${kopecks.toString().padStart(2, '0')}"
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits

    return buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(GROUP_SEPARATOR)
            append(char)
        }
    }
}
