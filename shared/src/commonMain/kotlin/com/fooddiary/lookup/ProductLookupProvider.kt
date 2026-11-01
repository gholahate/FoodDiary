package com.fooddiary.lookup

import com.fooddiary.data.repository.ProductRepository

data class ProductLookupResult(
    val name: String,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val fatPer100: Double,
    val carbPer100: Double,
    /** Non-null only for AI-sourced results; used to prompt user confirmation before saving. */
    val confidence: Float? = null,
)

/**
 * Abstraction over "where do search results for a product name come from".
 * MVP wires up [LocalDbLookupProvider] only. An AI-backed implementation can
 * be swapped in later (or composed alongside it) without changing any UI or
 * use-case code — see design doc §6.
 */
interface ProductLookupProvider {
    suspend fun search(query: String): List<ProductLookupResult>
}

/** Searches only the user's local product database — the MVP behavior. */
class LocalDbLookupProvider(
    private val productRepository: ProductRepository,
) : ProductLookupProvider {
    override suspend fun search(query: String): List<ProductLookupResult> =
        productRepository.search(query).map { product ->
            ProductLookupResult(
                name = product.name,
                kcalPer100 = product.macrosPer100.kcal,
                proteinPer100 = product.macrosPer100.protein,
                fatPer100 = product.macrosPer100.fat,
                carbPer100 = product.macrosPer100.carb,
                confidence = null,
            )
        }
}
