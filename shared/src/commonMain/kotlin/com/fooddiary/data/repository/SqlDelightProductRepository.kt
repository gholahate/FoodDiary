package com.fooddiary.data.repository

import com.fooddiary.db.FoodDiaryDatabase
import com.fooddiary.domain.CalcMode
import com.fooddiary.domain.CompositionLine
import com.fooddiary.domain.Macros
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductSource
import com.fooddiary.domain.ProductUnit
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightProductRepository(
    private val db: FoodDiaryDatabase,
) : ProductRepository {

    private val queries = db.productQueries
    private val compositionQueries = db.productCompositionQueries
    private val unitQueries = db.productUnitQueries

    override fun observeAll(): Flow<List<Product>> =
        queries.selectAllProducts()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun search(query: String): List<Product> = withContext(Dispatchers.Default) {
        queries.searchProducts(query).executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Product? = withContext(Dispatchers.Default) {
        queries.selectProductById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(product: Product) = withContext(Dispatchers.Default) {
        val existing = queries.selectProductById(product.id).executeAsOneOrNull()
        if (existing == null) {
            queries.insertProduct(
                id = product.id,
                name = product.name,
                calcMode = product.calcMode.name,
                kcalPer100 = product.macrosPer100.kcal,
                proteinPer100 = product.macrosPer100.protein,
                fatPer100 = product.macrosPer100.fat,
                carbPer100 = product.macrosPer100.carb,
                yieldGrams = product.yieldGrams,
                source = product.source.name,
                createdAt = product.createdAt,
                updatedAt = product.updatedAt,
            )
        } else {
            queries.updateProduct(
                name = product.name,
                calcMode = product.calcMode.name,
                kcalPer100 = product.macrosPer100.kcal,
                proteinPer100 = product.macrosPer100.protein,
                fatPer100 = product.macrosPer100.fat,
                carbPer100 = product.macrosPer100.carb,
                yieldGrams = product.yieldGrams,
                updatedAt = product.updatedAt,
                id = product.id,
            )
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.Default) {
        queries.deleteProduct(id)
    }

    override suspend fun getParentsUsingIngredient(productId: String): List<String> =
        withContext(Dispatchers.Default) {
            compositionQueries.selectParentsUsingIngredient(productId).executeAsList()
        }

    override suspend fun deleteWithOwnData(id: String) = withContext(Dispatchers.Default) {
        // Foreign keys aren't enforced by the driver, so clean up by hand.
        db.transaction {
            unitQueries.deleteUnitsForProduct(id)
            compositionQueries.deleteAllCompositionsForParent(id)
            queries.deleteProduct(id)
        }
    }

    override suspend fun getComposition(parentProductId: String): List<CompositionLine> =
        withContext(Dispatchers.Default) {
            compositionQueries.selectCompositionByParent(parentProductId).executeAsList().map {
                CompositionLine(
                    id = it.id,
                    parentProductId = it.parentProductId,
                    ingredientProductId = it.ingredientProductId,
                    amountGrams = it.amountGrams,
                )
            }
        }

    override suspend fun setComposition(parentProductId: String, lines: List<CompositionLine>) =
        withContext(Dispatchers.Default) {
            db.transaction {
                compositionQueries.deleteAllCompositionsForParent(parentProductId)
                for (line in lines) {
                    compositionQueries.insertComposition(
                        id = line.id,
                        parentProductId = parentProductId,
                        ingredientProductId = line.ingredientProductId,
                        amountGrams = line.amountGrams,
                    )
                }
            }
        }

    override suspend fun getAllCompositionEdges(): List<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            compositionQueries.selectAllCompositionEdges().executeAsList()
                .map { it.parentProductId to it.ingredientProductId }
        }

    override fun observeAllUnits(): Flow<List<ProductUnit>> =
        unitQueries.selectAllUnits()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { ProductUnit(it.id, it.productId, it.unitName, it.gramsPerUnit) } }

    override suspend fun getUnits(productId: String): List<ProductUnit> = withContext(Dispatchers.Default) {
        unitQueries.selectUnitsByProduct(productId).executeAsList().map {
            ProductUnit(it.id, it.productId, it.unitName, it.gramsPerUnit)
        }
    }

    override suspend fun upsertUnit(unit: ProductUnit) = withContext(Dispatchers.Default) {
        val existing = unitQueries.selectUnitById(unit.id).executeAsOneOrNull()
        if (existing == null) {
            unitQueries.insertProductUnit(unit.id, unit.productId, unit.unitName, unit.gramsPerUnit)
        } else {
            unitQueries.updateProductUnit(unit.unitName, unit.gramsPerUnit, unit.id)
        }
    }

    override suspend fun deleteUnit(id: String) = withContext(Dispatchers.Default) {
        unitQueries.deleteProductUnit(id)
    }
}

private fun com.fooddiary.db.Product.toDomain(): Product = Product(
    id = id,
    name = name,
    calcMode = CalcMode.valueOf(calcMode),
    macrosPer100 = Macros(kcalPer100, proteinPer100, fatPer100, carbPer100),
    yieldGrams = yieldGrams,
    source = ProductSource.valueOf(source),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
