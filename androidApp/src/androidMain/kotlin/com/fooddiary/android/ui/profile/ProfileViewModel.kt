package com.fooddiary.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ProfileUiState(
    val profileId: String = "",
    val name: String = "",
    val trackKcal: Boolean = false,
    val kcalText: String = "",
    val trackProtein: Boolean = false,
    val proteinText: String = "",
    val trackFat: Boolean = false,
    val fatText: String = "",
    val trackCarb: Boolean = false,
    val carbText: String = "",
    val isLoaded: Boolean = false,
    val errorMessage: String? = null,
    val savedJustNow: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class)
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        viewModelScope.launch {
            val existing = container.profileRepository.getProfile()
            _state.value = if (existing != null) {
                ProfileUiState(
                    profileId = existing.id,
                    name = existing.name,
                    trackKcal = existing.targetKcal != null,
                    kcalText = existing.targetKcal?.toCleanString() ?: "",
                    trackProtein = existing.targetProtein != null,
                    proteinText = existing.targetProtein?.toCleanString() ?: "",
                    trackFat = existing.targetFat != null,
                    fatText = existing.targetFat?.toCleanString() ?: "",
                    trackCarb = existing.targetCarb != null,
                    carbText = existing.targetCarb?.toCleanString() ?: "",
                    isLoaded = true,
                )
            } else {
                ProfileUiState(profileId = Uuid.random().toString(), isLoaded = true)
            }
        }
    }

    fun onNameChanged(name: String) {
        _state.value = _state.value.copy(name = name, savedJustNow = false)
        scheduleSave()
    }

    fun onTrackKcalChanged(track: Boolean) {
        _state.value = _state.value.copy(trackKcal = track, savedJustNow = false)
        scheduleSave()
    }

    fun onKcalTextChanged(text: String) {
        _state.value = _state.value.copy(kcalText = text, savedJustNow = false)
        scheduleSave()
    }

    fun onTrackProteinChanged(track: Boolean) {
        _state.value = _state.value.copy(trackProtein = track, savedJustNow = false)
        scheduleSave()
    }

    fun onProteinTextChanged(text: String) {
        _state.value = _state.value.copy(proteinText = text, savedJustNow = false)
        scheduleSave()
    }

    fun onTrackFatChanged(track: Boolean) {
        _state.value = _state.value.copy(trackFat = track, savedJustNow = false)
        scheduleSave()
    }

    fun onFatTextChanged(text: String) {
        _state.value = _state.value.copy(fatText = text, savedJustNow = false)
        scheduleSave()
    }

    fun onTrackCarbChanged(track: Boolean) {
        _state.value = _state.value.copy(trackCarb = track, savedJustNow = false)
        scheduleSave()
    }

    fun onCarbTextChanged(text: String) {
        _state.value = _state.value.copy(carbText = text, savedJustNow = false)
        scheduleSave()
    }

    private var saveJob: Job? = null

    /**
     * Autosave: every change is saved by itself ~0.6 s after the user stops
     * typing (no «Сохранить» button). A switched-on goal with an empty or
     * invalid number isn't saved yet — the previous value stays in effect and
     * a hint is shown until the field is fixed.
     */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            save()
        }
    }

    private suspend fun save() {
        val s = _state.value

        // A tracked macro needs a valid positive number; an untracked one is
        // simply stored as null regardless of whatever text is left in its field.
        fun resolve(track: Boolean, text: String): Result<Double?> {
            if (!track) return Result.success(null)
            val value = text.trim().replace(',', '.').toDoubleOrNull()
            return if (value == null || value <= 0.0) Result.failure(IllegalArgumentException()) else Result.success(value)
        }

        val kcal = resolve(s.trackKcal, s.kcalText)
        val protein = resolve(s.trackProtein, s.proteinText)
        val fat = resolve(s.trackFat, s.fatText)
        val carb = resolve(s.trackCarb, s.carbText)

        if (listOf(kcal, protein, fat, carb).any { it.isFailure }) {
            _state.value = _state.value.copy(
                errorMessage = "Укажите положительное число для каждого включённого параметра — до этого изменения не сохранятся",
                savedJustNow = false,
            )
            return
        }

        container.profileRepository.saveProfile(
            UserProfile(
                id = s.profileId,
                name = s.name.trim().ifBlank { "Мой профиль" },
                targetKcal = kcal.getOrNull(),
                targetProtein = protein.getOrNull(),
                targetFat = fat.getOrNull(),
                targetCarb = carb.getOrNull(),
            ),
        )
        _state.value = _state.value.copy(errorMessage = null, savedJustNow = true)
    }
}

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
