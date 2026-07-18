package com.findev.fintrack.ui

/**
 * Text-to-thousandths handling for meter readings.
 *
 * Separate from the money parser on purpose: a water meter reads to three decimals
 * (12345,678 m3), money to two, and one parser trying to serve both would silently round
 * a cubic metre to the nearest ten litres. Integer-only here as well - a reading is
 * counted in thousandths and never touches a Double.
 */

private const val DECIMAL_SEPARATOR = ','
private const val MAX_WHOLE_DIGITS = 8
private const val DECIMALS = 3

/** Keeps only what a reading may contain: digits, one separator, three decimals. */
fun sanitizeMeterInput(text: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    var decimals = 0

    for (char in text) {
        when {
            char.isDigit() && !separatorSeen -> {
                if (builder.length < MAX_WHOLE_DIGITS) builder.append(char)
            }

            char.isDigit() && decimals < DECIMALS -> {
                builder.append(char)
                decimals++
            }

            (char == ',' || char == '.') && !separatorSeen && builder.isNotEmpty() -> {
                builder.append(DECIMAL_SEPARATOR)
                separatorSeen = true
            }
        }
    }
    return builder.toString()
}

/** Parses sanitized text into thousandths. "12,5" is 12,500 - tenths, not thousandths. */
fun parseMeterToMilli(text: String): Long {
    val sanitized = sanitizeMeterInput(text)
    val separator = sanitized.indexOf(DECIMAL_SEPARATOR)

    val wholePart = if (separator >= 0) sanitized.substring(0, separator) else sanitized
    val fractionPart = if (separator >= 0) sanitized.substring(separator + 1) else ""

    val whole = wholePart.toLongOrNull() ?: 0L
    // "12," -> 12,000 and "12,5" -> 12,500: a trailing digit means tenths of a unit.
    val fraction = fractionPart.padEnd(DECIMALS, '0').toLongOrNull() ?: 0L

    return whole * 1000 + fraction
}

/** Renders thousandths back into the field, trimming zeros nobody types. */
fun formatMeterForInput(valueMilli: Long): String {
    if (valueMilli == 0L) return ""
    return formatMilli(valueMilli)
}

/** "12345,678", or "197" when the reading is whole. */
fun formatMilli(valueMilli: Long): String {
    val whole = valueMilli / 1000
    val fraction = valueMilli % 1000
    if (fraction == 0L) return whole.toString()
    return "$whole$DECIMAL_SEPARATOR${fraction.toString().padStart(3, '0').trimEnd('0')}"
}
