package com.fooddiary.android.ui.diary

/**
 * One rendered segment's layout: how much of the daily target box is filled
 * by "own" consumption (green, or red if this day alone exceeds target) vs.
 * "carried-in" overflow from another day (orange).
 */
data class DaySegmentLayout(
    val ownFraction: Float,     // 0f..1f (or beyond 1f only when isSelfOverGoal)
    val carryFraction: Float,   // 0f..1f, orange portion placed alongside "own"
    val isSelfOverGoal: Boolean, // this day's own total alone exceeds target -> solid red, carry-proof
)

/**
 * Result of redistributing weekly overflow: one [DaySegmentLayout] per input
 * day (same order/count as input), plus any amount that still didn't fit
 * anywhere in the week (rendered as an extra 8th purple segment).
 */
data class WeeklyOverflowLayout(
    val days: List<DaySegmentLayout>,
    val unplacedOverflowFraction: Float, // 0f if everything fit; else extra purple segment size
    val unplacedOverflowKcal: Double,    // the exact kcal amount behind that fraction, for the "+NNN" label
)

/**
 * Computes the visual-only overflow redistribution described by the user:
 *  - A day whose own consumption alone exceeds target is one-directional:
 *    it never RECEIVES incoming carry from other days (stays solid red,
 *    sized to its own total), but its own excess over target still becomes
 *    outgoing carry for other days, same as any other day's excess.
 *  - Processing days left-to-right (Mon..Sun), any carried-in overflow first
 *    tries to fill spare capacity in EARLIER days (backward, skipping
 *    self-exceeding days since they can't receive), then spills into the
 *    current day (orange, placed before that day's own green portion) —
 *    unless the current day is itself self-exceeding, in which case the
 *    carry skips straight through to become carry again for the next day.
 *    Whatever a normal day itself produces beyond target (own+carry) also
 *    becomes new carry for later days.
 *  - Overflow that still doesn't fit anywhere after Sunday becomes a single
 *    extra purple segment sized to the leftover amount.
 *
 * This never touches the underlying data — [dailyTotals] are the real
 * per-day kcal totals from the DB, [targetKcal] the user's daily goal; the
 * output is purely how to paint the 7 (or 8) segments.
 */
fun computeWeeklyOverflowLayout(dailyTotals: List<Double>, targetKcal: Double): WeeklyOverflowLayout {
    require(targetKcal > 0) { "targetKcal must be positive" }

    val n = dailyTotals.size
    val ownPlaced = DoubleArray(n)
    val carryPlaced = DoubleArray(n)
    val isSelfOver = BooleanArray(n) { dailyTotals[it] > targetKcal }

    // Self-exceeding days are immediately fixed: all "own", and this excess
    // is exactly what they'll emit as outgoing carry below. They never
    // receive incoming carry (handled by skipping them in the backward-fill
    // and forward-spill steps).
    for (i in 0 until n) {
        if (isSelfOver[i]) {
            ownPlaced[i] = dailyTotals[i] // may exceed targetKcal; rendered as solid red regardless
        }
    }

    /**
     * Puts [amount] of carry into days [0, beforeDay) that still have spare
     * capacity (own+carry < target), earliest first. Self-exceeding days can't
     * receive, so they're skipped. Returns what didn't fit.
     */
    fun fillBackward(amount: Double, beforeDay: Int): Double {
        var remaining = amount
        if (remaining <= 0.0) return remaining
        for (j in 0 until beforeDay) {
            if (isSelfOver[j]) continue
            val spare = targetKcal - (ownPlaced[j] + carryPlaced[j])
            if (spare > 0 && remaining > 0) {
                val placed = minOf(spare, remaining)
                carryPlaced[j] += placed
                remaining -= placed
            }
        }
        return remaining
    }

    var carry = 0.0
    for (i in 0 until n) {
        // Step 1: try to unload existing carry backward into earlier days with
        // spare capacity, earliest first.
        var remainingCarry = fillBackward(carry, beforeDay = i)

        if (isSelfOver[i]) {
            // Can't receive: whatever carry is still unplaced passes straight
            // through this day and becomes carry again for the next day, on
            // top of this day's own excess (computed below).
            carry = remainingCarry + (dailyTotals[i] - targetKcal)
            continue
        }

        // Step 2: whatever carry didn't fit backward spills into this day's
        // own box first (orange), up to this day's target capacity.
        val spillIntoThisDay = minOf(remainingCarry, targetKcal)
        carryPlaced[i] += spillIntoThisDay
        remainingCarry -= spillIntoThisDay

        // Step 3: this day's own consumption fills alongside/after the carry
        // portion. Whatever pushes the day's total (carry+own) past target
        // becomes new carry for subsequent days.
        val ownTotal = dailyTotals[i]
        val spaceLeftForOwn = targetKcal - carryPlaced[i]
        val ownThatFits = ownTotal.coerceIn(0.0, maxOf(0.0, spaceLeftForOwn))
        ownPlaced[i] = ownThatFits
        val ownOverflow = ownTotal - ownThatFits

        carry = remainingCarry + ownOverflow
    }

    // Step 1 for a day's excess normally runs at the start of the NEXT day.
    // The last day (Sunday) has no next day, so its excess used to skip the
    // backward pass and land straight in the purple segment even when earlier
    // days of the week still had room (bug: Sunday over goal, Tuesday half
    // empty -> everything went purple). Give it the same backward pass here.
    carry = fillBackward(carry, beforeDay = n)

    val days = (0 until n).map { i ->
        if (isSelfOver[i]) {
            DaySegmentLayout(
                ownFraction = (ownPlaced[i] / targetKcal).toFloat(),
                carryFraction = 0f,
                isSelfOverGoal = true,
            )
        } else {
            DaySegmentLayout(
                ownFraction = (ownPlaced[i] / targetKcal).toFloat().coerceIn(0f, 1f),
                carryFraction = (carryPlaced[i] / targetKcal).toFloat().coerceIn(0f, 1f),
                isSelfOverGoal = false,
            )
        }
    }

    val unplacedFraction = (carry / targetKcal).toFloat().coerceAtLeast(0f)
    return WeeklyOverflowLayout(
        days = days,
        unplacedOverflowFraction = unplacedFraction,
        unplacedOverflowKcal = carry.coerceAtLeast(0.0),
    )
}
