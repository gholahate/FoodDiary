package com.fooddiary.android

import android.content.Context
import com.fooddiary.android.data.AppSettings
import com.fooddiary.android.data.BackupManager
import com.fooddiary.android.data.UpdateManager
import com.fooddiary.android.data.OpenFoodFactsClient
import com.fooddiary.data.repository.SqlDelightDiaryRepository
import com.fooddiary.data.repository.SqlDelightProductRepository
import com.fooddiary.data.repository.SqlDelightUserProfileRepository
import com.fooddiary.db.DatabaseDriverFactory
import com.fooddiary.db.FoodDiaryDatabase
import app.cash.sqldelight.db.SqlDriver
import com.fooddiary.lookup.LocalDbLookupProvider
import com.fooddiary.usecase.AddDiaryEntryUseCase
import com.fooddiary.usecase.DeleteProductUseCase
import com.fooddiary.usecase.GetStatsUseCase
import com.fooddiary.usecase.SaveProductUseCase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Minimal hand-rolled dependency container. No framework (Koin/Hilt) yet —
 * intentional for the MVP stage; swap in a real DI framework once the app
 * grows past a handful of screens.
 */
@OptIn(ExperimentalUuidApi::class)
class AppContainer(context: Context) {
    // The driver is kept so the backup can flush it to disk and close it
    // before a database file is replaced (restore from backup).
    private val driver: SqlDriver = DatabaseDriverFactory(context.applicationContext).createDriver()
    private val database = FoodDiaryDatabase(driver)

    val productRepository = SqlDelightProductRepository(database)
    val diaryRepository = SqlDelightDiaryRepository(database)
    val profileRepository = SqlDelightUserProfileRepository(database)

    val productLookupProvider = LocalDbLookupProvider(productRepository)

    /** Barcode lookup in the open Open Food Facts database (needs internet). */
    val openFoodFacts = OpenFoodFactsClient()

    /** App preferences (theme etc.), stored outside the database. */
    val settings = AppSettings(context)

    /** Export / import of the whole diary to a file («Настройки → Резервная копия»). */
    val backup = BackupManager(context.applicationContext, driver, settings)

    /** New versions from GitHub Releases («О приложении» + check on start). */
    val updates = UpdateManager(context.applicationContext, settings, backup)

    private fun newId(): String = Uuid.random().toString()
    private fun nowEpochMillis(): Long = System.currentTimeMillis()

    val addDiaryEntryUseCase = AddDiaryEntryUseCase(
        productRepository = productRepository,
        diaryRepository = diaryRepository,
        idGenerator = ::newId,
        clock = ::nowEpochMillis,
    )

    val saveProductUseCase = SaveProductUseCase(
        productRepository = productRepository,
        idGenerator = ::newId,
        clock = ::nowEpochMillis,
    )

    val getStatsUseCase = GetStatsUseCase(diaryRepository)

    val deleteProductUseCase = DeleteProductUseCase(productRepository, diaryRepository)
}
