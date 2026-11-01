package com.fooddiary.android.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fooddiary.android.AppContainer

class DiaryDayViewModelFactory(
    private val container: AppContainer,
    private val initialDate: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == DiaryDayViewModel::class.java)
        return if (initialDate != null) {
            DiaryDayViewModel(container, initialDate)
        } else {
            DiaryDayViewModel(container)
        } as T
    }
}
