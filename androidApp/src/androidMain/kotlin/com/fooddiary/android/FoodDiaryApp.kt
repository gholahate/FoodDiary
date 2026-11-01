package com.fooddiary.android

import android.app.Application
import com.fooddiary.android.data.BackupManager

class FoodDiaryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // A restore from backup is finished here, before the database is opened.
        BackupManager.applyPendingRestore(this)
        container = AppContainer(this)
    }
}
