package com.fooddiary.data.repository

import com.fooddiary.domain.CompositionLine
import com.fooddiary.domain.DailyTotals
import com.fooddiary.domain.DiaryEntry
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductUnit
import com.fooddiary.domain.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * All data access goes through these interfaces, never through SQLDelight
 * queries directly from UI or use-case code. This is what lets the local-only
 * MVP later grow a second implementation (local cache + remote sync) for the
 * Telegram bot / cloud backup, without touching UI or use-case layers.
 */
interface ProductRepository {
    fun observeAll(): Flow<List<Product>>
    suspend fun search(query: String): List<Product>
    suspend fun getById(id: String): Product?
    suspend fun upsert(product: Product)
    suspend fun delete(id: String)

    /** Ids of composed products that list [productId] as an ingredient. */
    suspend fun getParentsUsingIngredient(productId: String): List<String>

    /** Deletes the product together with its own measures and its own composition, in one transaction. */
    suspend fun deleteWithOwnData(id: String)

    suspend fun getComposition(parentProductId: String): List<CompositionLine>
    suspend fun setComposition(parentProductId: String, lines: List<CompositionLine>)
    suspend fun getAllCompositionEdges(): List<Pair<String, String>> // (parentId, ingredientId)

    /** Live list of every product's household measures (for the catalog cards). */
    fun observeAllUnits(): Flow<List<ProductUnit>>
    suspend fun getUnits(productId: String): List<ProductUnit>
    suspend fun upsertUnit(unit: ProductUnit)
    suspend fun deleteUnit(id: String)
}

interface DiaryRepository {
    fun observeEntriesForDate(date: String): Flow<List<DiaryEntry>>
    fun observeDailyTotalsInRange(startDate: String, endDate: String): Flow<List<DailyTotals>>
    suspend fun getEntriesForDate(date: String): List<DiaryEntry>
    suspend fun addEntry(entry: DiaryEntry)
    suspend fun deleteEntry(id: String)
    suspend fun countEntriesForProduct(productId: String): Long
    suspend fun getDailyTotals(date: String): DailyTotals
    suspend fun getDailyTotalsInRange(startDate: String, endDate: String): List<DailyTotals>
}

interface UserProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
}
