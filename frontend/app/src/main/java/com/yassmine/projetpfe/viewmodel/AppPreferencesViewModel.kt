package com.yassmine.projetpfe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppPreferencesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val onboardingSeen: StateFlow<Boolean> = preferencesManager
        .getOnboardingSeen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val appLanguage: StateFlow<String> = preferencesManager
        .getAppLanguage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "fr")

    fun setOnboardingSeen(seen: Boolean = true) {
        viewModelScope.launch {
            preferencesManager.saveOnboardingSeen(seen)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesManager.saveAppLanguage(languageCode)
        }
    }
}
