package com.fooddiary.android.ui.history

import kotlinx.datetime.LocalDate

/**
 * One cell in the calendar grid: either a real date, or null padding before
 * the 1st of the month (so the grid always starts on Monday).
 */
data class CalendarCell(val date: LocalDate?)

/**
 * Builds a Monday-first calendar grid for [year]/[month] (month is 1-12):
 * leading null padding cells, then one cell per day of the month.
 */
fun buildCalendarGrid(year: Int, month: Int): List<CalendarCell> {
    val firstOfMonth = LocalDate(year, month, 1)
    val daysInMonth = daysInMonth(year, month)

    // kotlinx-datetime declares DayOfWeek as MONDAY..SUNDAY in that order, so
    // ordinal is already 0-based Monday-first (MONDAY=0 ... SUNDAY=6) — exactly
    // the number of blank cells needed before this weekday in a Monday-first grid.
    val leadingBlanks = firstOfMonth.dayOfWeek.ordinal

    val cells = mutableListOf<CalendarCell>()
    repeat(leadingBlanks) { cells += CalendarCell(null) }
    for (day in 1..daysInMonth) {
        cells += CalendarCell(LocalDate(year, month, day))
    }
    return cells
}

private fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeap(year)) 29 else 28
    else -> error("Invalid month: $month")
}

val MONTH_NAMES_RU = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

val WEEKDAY_SHORT_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
