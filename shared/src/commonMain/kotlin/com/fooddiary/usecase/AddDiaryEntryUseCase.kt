package com.fooddiary.usecase

import com.fooddiary.data.repository.DiaryRepository
import com.fooddiary.data.repository.ProductRepository
import com.fooddiary.domain.DiaryEntry

sealed class AddDiaryEntryResult {
    data class Success(val entry: DiaryEntry) : AddDiaryEntryResult()
    object ProductNotFound : AddDiaryEntryResult()
    object InvalidAmount : AddDiaryEntryResult()
}

/**
 * Adds a product to a given day's diary. Amount can be given directly in grams,
 * or via a household unit (id + count), which is resolved to grams first.
 * The resulting macro values are a snapshot (see design doc §1.5) — later edits
 * to the underlying product never retroactively change this entry.
 */
class AddDiaryEntryUseCase(
    private val productRepository: ProductRepository,
    private val diaryRepository: DiaryRepository,
    private val idGenerator: () -> String,
    private val clock: () -> Long,
) {
    suspend operator fun invoke(
        productId: String,
        entryDate: String,
        grams: Double? = null,
        unitId: String? = null,
        unitCount: Double? = null,
    ): AddDiaryEntryResult {
        val product = productRepository.getById(productId) ?: return AddDiaryEntryResult.ProductNotFound

        val amountGrams: Double = when {
            grams != null -> grams
            unitId != null && unitCount != null -> {
                val unit = productRepository.getUnits(productId).find { it.id == unitId }
                    ?: return AddDiaryEntryResult.ProductNotFound
                unit.gramsPerUnit * unitCount
            }
            else -> return AddDiaryEntryResult.InvalidAmount
        }

        if (amountGrams <= 0.0) return AddDiaryEntryResult.InvalidAmount

        val entry = DiaryEntry(
            id = idGenerator(),
            productId = productId,
            entryDate = entryDate,
            amountGrams = amountGrams,
            unitUsedId = unitId,
            macros = product.macrosPer100.scaledTo(amountGrams),
            createdAt = clock(),
        )
        diaryRepository.addEntry(entry)
        return AddDiaryEntryResult.Success(entry)
    }
}
