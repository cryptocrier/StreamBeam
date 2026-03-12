package com.stremioclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stremioclone.cast.CastManager

/**
 * Factory for creating ViewModels with dependencies.
 */
class PlayerViewModelFactory(
    private val castManager: CastManager
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> {
                PlayerViewModel(castManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
