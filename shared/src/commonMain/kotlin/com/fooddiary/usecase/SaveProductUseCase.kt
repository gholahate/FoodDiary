package com.fooddiary.usecase

import com.fooddiary.data.repository.ProductRepository
import com.fooddiary.domain.CalcMode
import com.fooddiary.domain.CompositionCalculator
import com.fooddiary.domain.CompositionEdge
import com.fooddiary.domain.CompositionLine
import com.fooddiary.domain.Product

sealed class SaveProductResult {
    data class Success(val product: Product) : SaveProductResult()
    /** One or more proposed composition lines would introduce a cycle in the ingredient graph. */
    data class CycleDetected(val conflictingIngredientId: String) : SaveProductResult()
    /** COMPUTED product is missing yieldGrams or has no ingredients — can't derive macros. */
    object IncompleteComposition : SaveProductResult()
}

/**
 * Persists a product (MANUAL or COMPUTED) together with its ingredient
 * composition (if any), then triggers a bottom-up recompute (design doc §3)
 * of this product and any other COMPUTED product that transitively depends
 * on it, so cached per-100g macros stay correct across the whole graph.
 */
class SaveProductUseCase(
    private val productRepository: ProductRepository,
    private val idGenerator: () -> String,
    private val clock: () -> Long,
    private val calculator: CompositionCalculator = CompositionCalculator(),
) {
    suspend operator fun invoke(
        product: Product,
        compositionLines: List<CompositionLine> = emptyList(),
    ): SaveProductResult {
        if (product.calcMode == CalcMode.COMPUTED) {
            if (product.yieldGrams == null || product.yieldGrams <= 0.0 || compositionLines.isEmpty()) {
                return SaveProductResult.IncompleteComposition
            }

            val existingEdges = productRepository.getAllCompositionEdges()
                .map { CompositionEdge(it.first, it.second) }
                // Drop this product's own prior edges — we're about to replace them wholesale.
                .filter { it.parentProductId != product.id }

            for (line in compositionLines) {
                if (calculator.wouldCreateCycle(existingEdges, product.id, line.ingredientProductId)) {
                    return SaveProductResult.CycleDetected(line.ingredientProductId)
                }
            }
        }

        val now = clock()
        val existingProduct = productRepository.getById(product.id)
        val toSave = product.copy(
            updatedAt = now,
            createdAt = existingProduct?.createdAt ?: now,
        )
        productRepository.upsert(toSave)

        if (product.calcMode == CalcMode.COMPUTED) {
            productRepository.setComposition(
                product.id,
                compositionLines.map { it.copy(id = it.id.ifEmpty { idGenerator() }, parentProductId = product.id) },
            )
        } else {
            // MANUAL products never carry stale composition rows.
            productRepository.setComposition(product.id, emptyList())
        }

        recomputeAffected(product.id)

        val saved = productRepository.getById(product.id) ?: toSave
        return SaveProductResult.Success(saved)
    }

    /**
     * Recomputes cached macros for every COMPUTED product in the database.
     * Simpler and safer than trying to compute just the "affected" subset —
     * the graph is expected to stay small (personal food diary, not an
     * industrial recipe database), so a full recompute is cheap and avoids
     * subtle bugs from partial dependency tracking.
     */
    private suspend fun recomputeAffected(@Suppress("UNUSED_PARAMETER") changedProductId: String) {
        val all = productRepository.observeAllSnapshot()
        val productsById = all.associateBy { it.id }
        val compositionsByParent = all
            .filter { it.calcMode == CalcMode.COMPUTED }
            .associate { p -> p.id to productRepository.getComposition(p.id) }

        val recomputed = calculator.recomputeAll(productsById, compositionsByParent)
        val now = clock()
        for ((id, macros) in recomputed) {
            val existing = productsById[id] ?: continue
            productRepository.upsert(existing.copy(macrosPer100 = macros, updatedAt = now))
        }
    }
}

/** Convenience one-shot read, since ProductRepository only exposes a Flow for "all". */
private suspend fun ProductRepository.observeAllSnapshot(): List<Product> {
    // Implemented via search with empty query, which matches everything (LIKE '%%').
    return search("")
}
