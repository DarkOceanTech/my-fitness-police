package com.darkoceantech.myfitnesspolice.domain.formatting

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class WeightFormatTest {
    @Test fun roundsHalfUpAndGroupsLargeValuesWithoutAbbreviations() {
        assertEquals("0", formatWeight(BigDecimal.ZERO))
        assertEquals("65", formatWeight(BigDecimal("64.5")))
        assertEquals("64", formatWeight(BigDecimal("64.499")))
        assertEquals("1,235", formatPoundsText("1234.5"))
        assertEquals("1,000,000", formatWeight(BigDecimal("999999.5")))
        assertEquals("—", formatPoundsText("—"))
    }
    @Test fun storedGramsAreConvertedBeforeRoundingAndNeverAltered() {
        val grams = 560170L
        assertEquals("1,235", formatSessionPounds(grams))
        assertEquals(560170L, grams)
        assertEquals("1", formatSessionPounds(227L))
        assertEquals("0", formatSessionPounds(226L))
    }
    @Test fun commaGroupingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("12,346", formatWeight(12345.5))
        } finally { Locale.setDefault(previous) }
    }
}
