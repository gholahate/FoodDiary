package com.fooddiary.usecase

import com.fooddiary.data.repository.DiaryRepository
import com.fooddiary.domain.DailyTotals
import com.fooddiary.domain.Macros
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class StatsStep { DAY, WEEK }

data class StatsPoint(
    val label: String,       // "2026-09-22" for DAY, "2026-W38" for WEEK
    val macros: Macros,
)

/**
 * Produces chart-ready points for the Charts screen and the Today mini-chart.
 * WEEK step sums all days in each ISO week (design doc §5: confirmed as sum,
 * not average).
 */
class GetStatsUseCase(
    private val diaryRepository: DiaryRepository,
) {
    suspend operator fun invoke(
        startDate: String,
        endDate: String,
        step: StatsStep,
    ): List<StatsPoint> {
        val daily = diaryRepository.getDailyTotalsInRange(startDate, endDate)
        return toPoints(daily, step)
    }

    /**
     * Live version of [invoke]: emits a fresh list every time diary entries
     * inside the range are added or removed, so a screen that stays alive
     * (like the Charts tab) never shows a stale snapshot.
     */
    fun observe(
        startDate: String,
        endDate: String,
        step: StatsStep,
    ): Flow<List<StatsPoint>> =
        diaryRepository.observeDailyTotalsInRange(startDate, endDate)
            .map { daily -> toPoints(daily, step) }

    private fun toPoints(daily: List<DailyTotals>, step: StatsStep): List<StatsPoint> =
        when (step) {
            StatsStep.DAY -> daily.map { StatsPoint(it.date, it.macros) }
            StatsStep.WEEK -> bucketByIsoWeek(daily)
        }

    private fun bucketByIsoWeek(daily: List<DailyTotals>): List<StatsPoint> {
        val buckets = LinkedHashMap<String, Macros>()
        for (dayTotal in daily) {
            val weekLabel = isoWeekLabel(dayTotal.date)
            buckets[weekLabel] = (buckets[weekLabel] ?: Macros.ZERO) + dayTotal.macros
        }
        return buckets.map { (label, macros) -> StatsPoint(label, macros) }
    }
}

/**
 * Computes an ISO-8601 week label ("YYYY-Www") for a "YYYY-MM-DD" date string,
 * without relying on any platform-specific date library, so this stays usable
 * from commonMain on both Android and iOS targets.
 */
internal fun isoWeekLabel(isoDate: String): String {
    val (year, month, day) = isoDate.split("-").map { it.toInt() }
    val epochDay = daysSinceEpoch(year, month, day)
    // ISO week date algorithm
    val weekday = ((epochDay + 3) % 7 + 7) % 7 + 1 // Mon=1..Sun=7, epoch (1970-01-01) was a Thursday
    val thursdayEpochDay = epochDay - weekday + 4
    val thursdayYear = yearFromEpochDay(thursdayEpochDay)
    val jan1Epoch = daysSinceEpoch(thursdayYear, 1, 1)
    val week = (thursdayEpochDay - jan1Epoch) / 7 + 1
    return "$thursdayYear-W" + week.toString().padStart(2, '0')
}

private fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private val cumDaysNonLeap = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
    var days = 0L
    if (year >= 1970) {
        for (y in 1970 until year) days += if (isLeap(y)) 366 else 365
    } else {
        for (y in year until 1970) days -= if (isLeap(y)) 366 else 365
    }
    days += cumDaysNonLeap[month - 1]
    if (month > 2 && isLeap(year)) days += 1
    days += (day - 1)
    return days
}

private fun yearFromEpochDay(epochDay: Long): Int {
    var year = 1970
    var remaining = epochDay
    if (remaining >= 0) {
        while (true) {
            val len = if (isLeap(year)) 366 else 365
            if (remaining < len) break
            remaining -= len
            year++
        }
    } else {
        while (remaining < 0) {
            year--
            remaining += if (isLeap(year)) 366 else 365
        }
    }
    return year
}
