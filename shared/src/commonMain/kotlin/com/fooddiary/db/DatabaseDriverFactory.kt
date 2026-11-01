package com.fooddiary.db

import app.cash.sqldelight.db.SqlDriver

/** Platform-specific factory for the SQLDelight driver — implemented per target. */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): FoodDiaryDatabase =
    FoodDiaryDatabase(driverFactory.createDriver())
