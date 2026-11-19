package com.adi.bitlifesaveeditor

import java.math.BigDecimal

/**
 * Parses common Indonesian/European and US number grouping while rejecting
 * values that would silently change when stored as the save's Float64 balance.
 */
internal object BalanceAmountParser {
    private val usGrouped = Regex("""^[0-9]{1,3}(,[0-9]{3})+(\.[0-9]+)?$""")
    private val euGrouped = Regex("""^[0-9]{1,3}(\.[0-9]{3})+(,[0-9]+)?$""")
    private val plainDecimal = Regex("""^[0-9]+([.,][0-9]+)?$""")

    fun parse(raw: String): Double? {
        val value = raw.trim().replace(" ", "")
        if (value.isEmpty()) return null

        val normalized = when {
            usGrouped.matches(value) -> value.replace(",", "")
            euGrouped.matches(value) -> value.replace(".", "").replace(",", ".")
            plainDecimal.matches(value) -> value.replace(",", ".")
            else -> return null
        }

        val decimal = normalized.toBigDecimalOrNull() ?: return null
        if (decimal.signum() < 0) return null

        val amount = decimal.toDouble()
        if (!amount.isFinite() || amount > BalancePatchEngine.MAX_BALANCE) return null

        // BigDecimal.valueOf uses the shortest decimal that round-trips to the
        // same Double. Comparing it to the input prevents silent rounding such
        // as 9007199254740993 becoming 9007199254740992.
        if (BigDecimal.valueOf(amount).compareTo(decimal.stripTrailingZeros()) != 0) return null
        return amount
    }
}
