package com.fooddiary.android.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fooddiary.android.AppContainer

class ProductEditorViewModelFactory(
    private val container: AppContainer,
    private val existingProductId: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ProductEditorViewModel::class.java)
        return ProductEditorViewModel(container, existingProductId) as T
    }
}
