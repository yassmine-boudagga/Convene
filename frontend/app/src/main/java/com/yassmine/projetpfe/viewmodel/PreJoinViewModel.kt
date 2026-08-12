package com.yassmine.projetpfe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PreJoinViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _cameraEnabled = MutableStateFlow(true)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled.asStateFlow()

    private val _micEnabled = MutableStateFlow(true)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.getPreJoinCameraEnabled().collect { value ->
                _cameraEnabled.value = value
            }
        }
        viewModelScope.launch {
            preferencesManager.getPreJoinMicEnabled().collect { value ->
                _micEnabled.value = value
            }
        }
    }

    suspend fun savePreferences(cameraEnabled: Boolean, micEnabled: Boolean) {
        persistPreferences(cameraEnabled, micEnabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistPreferences(enabled, _micEnabled.value)
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            persistPreferences(_cameraEnabled.value, enabled)
        }
    }

    private suspend fun persistPreferences(cameraEnabled: Boolean, micEnabled: Boolean) {
        _cameraEnabled.value = cameraEnabled
        _micEnabled.value = micEnabled
        preferencesManager.savePreJoinPreferences(cameraEnabled, micEnabled)
    }
}
