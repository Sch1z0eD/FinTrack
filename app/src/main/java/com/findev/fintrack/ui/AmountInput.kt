package com.findev.fintrack.ui

/**
 * Text-to-kopecks handling for the system-keyboard amount field.
 *
 * The system keyboard lets the user type anything, so the text has to be constrained
 * and parsed by hand. Everything here is integer-only: no Double ever touches money.
 */

private const val DECIMAL_SEPARATOR = ','
private const val MAX_RUBLE_DIGITS = 9

/**
 * Keeps only what a rouble amount may contain: digits, one separator, two decimals.
 * Both ',' and '.' are accepted because vendor keyboards disagree on which one they offer.
 */
fun sanitizeAmountInput(text: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    var decimals = 0

    for (char in text) {
        when {
            char.isDigit() && !separatorSeen -> {
                if (builder.length < MAX_RUBLE_DIGITS) builder.append(char)
            }

            char.isDigit() && decimals < 2 -> {
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

/** Parses sanitized text into kopecks. "12,3" is 12 roubles 30 kopecks, not 12.03. */
fun parseAmountToMinor(text: String): Long {
    val sanitized = sanitizeAmountInput(text)
    val separator = sanitized.indexOf(DECIMAL_SEPARATOR)

    val rublesPart = if (separator >= 0) sanitized.substring(0, separator) else sanitized
    val kopecksPart = if (separator >= 0) sanitized.substring(separator + 1) else ""

    val rubles = rublesPart.toLongOrNull() ?: 0L
    // "5," -> 5,00 and "5,3" -> 5,30: a trailing digit means tenths of a rouble.
    val kopecks = kopecksPart.padEnd(2, '0').toLongOrNull() ?: 0L

    return rubles * 100 + kopecks
}

/**
 * Same as [sanitizeAmountInput] but keeps three decimals instead of two.
 *
 * Without this the money sanitizer silently eats the third digit as it is typed, so
 * "28,572" becomes "28,57" in the field and the extra precision the storage unit was
 * widened for never reaches it.
 */
fun sanitizeRateInput(text: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    var decimals = 0

    for (char in text) {
        when {
            char.isDigit() && !separatorSeen -> {
                // A rate needs far fewer digits than money; three keeps 999% reachable
                // and nonsense out.
                if (builder.length < 3) builder.append(char)
            }

            char.isDigit() && decimals < 3 -> {
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

/**
 * Reads an interest rate into thousandths of a percent: "28,572" -> 28572.
 *
 * Separate from [parseAmountToMinor] because money has two decimals and a rate has three.
 * Feeding a rate through the money parser silently drops the third digit, which is how
 * 28,572% would quietly become 28,57%.
 */
fun parseRateToMilliPercent(text: String): Int {
    val sanitized = sanitizeRateInput(text)
    val separator = sanitized.indexOf(DECIMAL_SEPARATOR)

    val wholePart = if (separator >= 0) sanitized.substring(0, separator) else sanitized
    val fractionPart = if (separator >= 0) sanitized.substring(separator + 1) else ""

    val whole = wholePart.toLongOrNull() ?: 0L
    // "28,5" -> 28,500 and "28," -> 28,000. Anything past three digits is not a rate.
    val fraction = fractionPart.take(3).padEnd(3, '0').toLongOrNull() ?: 0L

    return (whole * 1000 + fraction).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Renders a rate back into the field, trimming the zeros the storage unit forces. */
fun formatRateForInput(milliPercent: Int): String {
    if (milliPercent == 0) return ""
    val whole = milliPercent / 1000
    val fraction = milliPercent % 1000
    if (fraction == 0) return whole.toString()
    return "$whole$DECIMAL_SEPARATOR" + fraction.toString().padStart(3, '0').trimEnd('0')
}

/** Renders kopecks back into the field, e.g. for editing an existing transaction. */
fun formatAmountForInput(amountMinor: Long): String {
    if (amountMinor == 0L) return ""
    val rubles = amountMinor / 100
    val kopecks = amountMinor % 100
    return if (kopecks == 0L) {
        rubles.toString()
    } else {
        "$rubles$DECIMAL_SEPARATOR${kopecks.toString().padStart(2, '0')}"
    }
}
