package com.fooddiary.domain

/** How a product's per-100g macros are determined. */
enum class CalcMode { MANUAL, COMPUTED }

/** Where a product record came from — relevant once AI lookup / seed data exist. */
enum class ProductSource {
    USER,
    AI,
    SEED,

    /**
     * Imported by scanning a barcode and looking it up in the open
     * Open Food Facts database. Stored as plain text in Product.source, so
     * adding this value needs no database migration.
     */
    OPEN_FOOD_FACTS,
}

/**
 * A single macro/calorie quadruplet. Used both for per-100g values and for
 * absolute (scaled) snapshot values, depending on context.
 */
data class Macros(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
) {
    operator fun plus(other: Macros) = Macros(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carb = carb + other.carb,
    )

    /** Scale per-100g macros to an arbitrary gram amount. */
    fun scaledTo(grams: Double): Macros = Macros(
        kcal = kcal * grams / 100.0,
        protein = protein * grams / 100.0,
        fat = fat * grams / 100.0,
        carb = carb * grams / 100.0,
    )

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
    }
}

data class Product(
    val id: String,
    val name: String,
    val calcMode: CalcMode,
    val macrosPer100: Macros,
    val yieldGrams: Double?,
    val source: ProductSource,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One ingredient line inside a composed product's recipe. */
data class CompositionLine(
    val id: String,
    val parentProductId: String,
    val ingredientProductId: String,
    val amountGrams: Double,
)

data class ProductUnit(
    val id: String,
    val productId: String,
    val unitName: String,
    val gramsPerUnit: Double,
)

data class UserProfile(
    val id: String,
    val name: String,
    val targetKcal: Double?,
    val targetProtein: Double?,
    val targetFat: Double?,
    val targetCarb: Double?,
) {
    /** True if the user has opted to track at least one macro. */
    fun hasAnyGoal() = targetKcal != null || targetProtein != null || targetFat != null || targetCarb != null
}

data class DiaryEntry(
    val id: String,
    val productId: String,
    val entryDate: String, // YYYY-MM-DD
    val amountGrams: Double,
    val unitUsedId: String?,
    val macros: Macros, // snapshot at time of entry
    val createdAt: Long,
)

/** Aggregated totals for one calendar day, used by daily view + charts. */
data class DailyTotals(
    val date: String,
    val macros: Macros,
)
