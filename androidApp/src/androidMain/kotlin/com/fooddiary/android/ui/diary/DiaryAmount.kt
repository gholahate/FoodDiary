package com.fooddiary.android.ui.diary

/** How much of a product the user is adding to the diary, exactly as they entered it. */
sealed interface DiaryAmount {
    /** Plain weight, in grams. */
    data class Grams(val grams: Double) : DiaryAmount

    /**
     * [count] units of a saved household measure (e.g. 2 teaspoons). The grams
     * are derived from the measure's saved weight (2 x 10 g = 20 g), and the
     * macros are then scaled from the product's per-100g values.
     */
    data class Measure(val unitId: String, val count: Double) : DiaryAmount
}
