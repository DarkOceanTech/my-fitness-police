package com.darkoceantech.myfitnesspolice.domain.formatting

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Presentation only: stored grams and numeric picker values retain their original precision. */
fun formatWeight(pounds: BigDecimal): String = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).apply {
    roundingMode = RoundingMode.HALF_UP
}.format(pounds)

fun formatWeight(pounds: Double): String = formatWeight(BigDecimal.valueOf(pounds))
fun formatPoundsText(value: String): String = value.toBigDecimalOrNull()?.let(::formatWeight) ?: value
fun formatSessionPounds(grams: Long): String = formatWeight(BigDecimal.valueOf(grams).divide(BigDecimal("453.59237"), 9, RoundingMode.HALF_UP))
