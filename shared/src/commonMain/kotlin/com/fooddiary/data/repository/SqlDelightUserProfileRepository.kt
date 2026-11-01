package com.fooddiary.data.repository

import com.fooddiary.db.FoodDiaryDatabase
import com.fooddiary.domain.UserProfile
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightUserProfileRepository(
    private val db: FoodDiaryDatabase,
) : UserProfileRepository {

    private val queries = db.userProfileQueries

    override fun observeProfile(): Flow<UserProfile?> =
        queries.selectProfile().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toDomain() }

    override suspend fun getProfile(): UserProfile? = withContext(Dispatchers.Default) {
        queries.selectProfile().executeAsOneOrNull()?.toDomain()
    }

    override suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.Default) {
        val existing = queries.selectProfile().executeAsOneOrNull()
        if (existing == null) {
            queries.insertProfile(
                id = profile.id,
                name = profile.name,
                targetKcal = profile.targetKcal,
                targetProtein = profile.targetProtein,
                targetFat = profile.targetFat,
                targetCarb = profile.targetCarb,
            )
        } else {
            queries.updateProfile(
                name = profile.name,
                targetKcal = profile.targetKcal,
                targetProtein = profile.targetProtein,
                targetFat = profile.targetFat,
                targetCarb = profile.targetCarb,
                id = existing.id,
            )
        }
    }
}

private fun com.fooddiary.db.UserProfile.toDomain(): UserProfile = UserProfile(
    id = id,
    name = name,
    targetKcal = targetKcal,
    targetProtein = targetProtein,
    targetFat = targetFat,
    targetCarb = targetCarb,
)
