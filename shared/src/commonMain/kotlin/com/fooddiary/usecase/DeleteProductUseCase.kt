package com.fooddiary.usecase

import com.fooddiary.data.repository.DiaryRepository
import com.fooddiary.data.repository.ProductRepository

sealed interface DeleteProductResult {
    data object Deleted : DeleteProductResult

    /**
     * Not deleted because it is still in use: diary entries point at it (their
     * names would be lost) and/or composed products list it as an ingredient
     * (their КБЖУ would break).
     */
    data class InUse(val diaryEntries: Long, val usedInProducts: List<String>) : DeleteProductResult
}

/**
 * Deletes a product from the base only if nothing references it; otherwise
 * reports what uses it so the UI can explain. Deleting removes the product's
 * own measures and composition too.
 */
class DeleteProductUseCase(
    private val productRepository: ProductRepository,
    private val diaryRepository: DiaryRepository,
) {
    suspend operator fun invoke(productId: String): DeleteProductResult {
        val entries = diaryRepository.countEntriesForProduct(productId)
        val parents = productRepository.getParentsUsingIngredient(productId)
            .mapNotNull { productRepository.getById(it)?.name }
        if (entries > 0 || parents.isNotEmpty()) return DeleteProductResult.InUse(entries, parents)
        productRepository.deleteWithOwnData(productId)
        return DeleteProductResult.Deleted
    }
}
