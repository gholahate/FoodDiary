package com.fooddiary.domain

/**
 * Pure, DB-agnostic logic for:
 *  1) detecting cycles before a new composition edge is inserted
 *  2) computing per-100g macros for COMPUTED products from their ingredient tree,
 *     bottom-up, to arbitrary depth
 *
 * Kept free of SQLDelight types so it's trivially unit-testable and reusable
 * from any platform target.
 */
class CompositionCalculator {

    /**
     * Returns true if adding an edge ingredientId -> parentId (parent "contains" ingredient)
     * would create a cycle, given the existing edges.
     *
     * existingEdges: list of (parentProductId, ingredientProductId) pairs already in the DB.
     * A cycle exists if `parentId` is reachable from `ingredientId` by following the
     * "ingredient is itself composed of" chain — i.e. if ingredientId depends (directly or
     * transitively) on parentId, adding parentId->ingredientId would close a loop.
     */
    fun wouldCreateCycle(
        existingEdges: List<CompositionEdge>,
        newParentId: String,
        newIngredientId: String,
    ): Boolean {
        if (newParentId == newIngredientId) return true // self-reference

        // Build adjacency: parent -> list of its ingredients
        val childrenOf: Map<String, List<String>> =
            (existingEdges + CompositionEdge(newParentId, newIngredientId))
                .groupBy({ it.parentProductId }, { it.ingredientProductId })

        // DFS from newParentId following "is ingredient of" edges; if we ever reach
        // newParentId again, there's a cycle.
        val visited = mutableSetOf<String>()
        fun dfs(node: String): Boolean {
            if (!visited.add(node)) return false
            val children = childrenOf[node].orEmpty()
            for (child in children) {
                if (child == newParentId) return true
                if (dfs(child)) return true
            }
            return false
        }

        return dfs(newIngredientId)
    }

    /**
     * Computes per-100g macros for every COMPUTED product in [products], resolving
     * nested composed ingredients to arbitrary depth. MANUAL products are returned
     * as-is (their stored macrosPer100 is authoritative).
     *
     * @param products all known products, keyed by id
     * @param compositionsByParent adjacency: parentProductId -> its ingredient lines
     * @return map of productId -> newly computed Macros (per 100g) for every COMPUTED
     *         product whose value could be resolved. Products with unresolved/missing
     *         ingredients or yieldGrams are omitted (caller should treat as "leave unchanged"
     *         and surface a validation error to the user).
     */
    fun recomputeAll(
        products: Map<String, Product>,
        compositionsByParent: Map<String, List<CompositionLine>>,
    ): Map<String, Macros> {
        val result = mutableMapOf<String, Macros>()
        val inProgress = mutableSetOf<String>() // guards against residual cycles at runtime

        fun resolve(productId: String): Macros? {
            result[productId]?.let { return it }
            val product = products[productId] ?: return null

            if (product.calcMode == CalcMode.MANUAL) {
                result[productId] = product.macrosPer100
                return product.macrosPer100
            }

            // COMPUTED: needs yieldGrams and a fully resolvable ingredient list
            val yield = product.yieldGrams
            val lines = compositionsByParent[productId]
            if (yield == null || yield <= 0.0 || lines.isNullOrEmpty()) return null

            if (!inProgress.add(productId)) return null // cycle guard, should not happen if pre-checked
            var total = Macros.ZERO
            for (line in lines) {
                val ingredientPer100 = resolve(line.ingredientProductId) ?: run {
                    inProgress.remove(productId)
                    return null
                }
                total += ingredientPer100.scaledTo(line.amountGrams)
            }
            inProgress.remove(productId)

            // total is absolute macros for the whole batch (yieldGrams); normalize back to per-100g
            val per100 = Macros(
                kcal = total.kcal * 100.0 / yield,
                protein = total.protein * 100.0 / yield,
                fat = total.fat * 100.0 / yield,
                carb = total.carb * 100.0 / yield,
            )
            result[productId] = per100
            return per100
        }

        for (id in products.keys) {
            if (products[id]?.calcMode == CalcMode.COMPUTED) resolve(id)
        }
        return result
    }
}

data class CompositionEdge(
    val parentProductId: String,
    val ingredientProductId: String,
)
