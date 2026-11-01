package com.fooddiary.data.repository

import com.fooddiary.db.FoodDiaryDatabase
import com.fooddiary.domain.DailyTotals
import com.fooddiary.domain.DiaryEntry
import com.fooddiary.domain.Macros
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightDiaryRepository(
    private val db: FoodDiaryDatabase,
) : DiaryRepository {

    private val queries = db.diaryEntryQueries

    override fun observeEntriesForDate(date: String): Flow<List<DiaryEntry>> =
        queries.selectEntriesForDate(date)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDailyTotalsInRange(startDate: String, endDate: String): Flow<List<DailyTotals>> =
        queries.selectDailyTotalsInRange(startDate, endDate)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { DailyTotals(it.entryDate, Macros(it.totalKcal, it.totalProtein, it.totalFat, it.totalCarb)) }
            }

    override suspend fun getEntriesForDate(date: String): List<DiaryEntry> = withContext(Dispatchers.Default) {
        queries.selectEntriesForDate(date).executeAsList().map { it.toDomain() }
    }

    override suspend fun addEntry(entry: DiaryEntry) = withContext(Dispatchers.Default) {
        queries.insertEntry(
            id = entry.id,
            productId = entry.productId,
            entryDate = entry.entryDate,
            amountGrams = entry.amountGrams,
            unitUsedId = entry.unitUsedId,
            kcal = entry.macros.kcal,
            protein = entry.macros.protein,
            fat = entry.macros.fat,
            carb = entry.macros.carb,
            createdAt = entry.createdAt,
        )
    }

    override suspend fun deleteEntry(id: String) = withContext(Dispatchers.Default) {
        queries.deleteEntry(id)
    }

    override suspend fun countEntriesForProduct(productId: String): Long = withContext(Dispatchers.Default) {
        queries.countEntriesForProduct(productId).executeAsOne()
    }

    override suspend fun getDailyTotals(date: String): DailyTotals = withContext(Dispatchers.Default) {
        val row = queries.selectDailyTotals(date).executeAsOne()
        DailyTotals(date, Macros(row.totalKcal, row.totalProtein, row.totalFat, row.totalCarb))
    }

    /**
     * Returns per-day totals for the range. Weekly aggregation (step = week, per
     * design doc §5: sum-per-week, not average) is done by the caller (use-case
     * layer) by bucketing these daily totals into ISO weeks — kept out of SQL to
     * stay portable across the SQLite date-function quirks between platforms.
     */
    override suspend fun getDailyTotalsInRange(startDate: String, endDate: String): List<DailyTotals> =
        withContext(Dispatchers.Default) {
            queries.selectDailyTotalsInRange(startDate, endDate).executeAsList().map {
                DailyTotals(it.entryDate, Macros(it.totalKcal, it.totalProtein, it.totalFat, it.totalCarb))
            }
        }
}

private fun com.fooddiary.db.DiaryEntry.toDomain(): DiaryEntry = DiaryEntry(
    id = id,
    productId = productId,
    entryDate = entryDate,
    amountGrams = amountGrams,
    unitUsedId = unitUsedId,
    macros = Macros(kcal, protein, fat, carb),
    createdAt = createdAt,
)
