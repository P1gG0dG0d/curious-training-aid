package com.haoqi.training.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haoqi.training.ai.DeepSeekClient
import com.haoqi.training.data.db.ProfileEntity
import com.haoqi.training.data.prefs.ApiConfig
import com.haoqi.training.data.prefs.ApiConfigStore
import com.haoqi.training.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

data class SettingsUiState(
    val profile: ProfileEntity = ProfileEntity(),
    val apiConfig: ApiConfig = ApiConfig(),
    val testState: TestState = TestState.Idle,
)

class SettingsViewModel(
    private val profileRepo: ProfileRepository,
    private val apiConfigStore: ApiConfigStore,
) : ViewModel() {

    private val testState = MutableStateFlow<TestState>(TestState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepo.profileFlow,
        apiConfigStore.config,
        testState,
    ) { profile, apiConfig, test ->
        SettingsUiState(profile = profile ?: ProfileEntity(), apiConfig = apiConfig, testState = test)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { profileRepo.ensureDefault() }
    }

    fun saveProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepo.saveProfile(profile.copy(id = 1, configured = true)) }
    }

    fun saveApiConfig(config: ApiConfig) {
        apiConfigStore.save(config)
        testState.value = TestState.Idle
    }

    fun testConnection(config: ApiConfig) {
        testState.value = TestState.Testing
        viewModelScope.launch {
            DeepSeekClient().testConnection(config)
                .onSuccess { testState.value = TestState.Ok(it) }
                .onFailure { testState.value = TestState.Failed(it.message ?: "连接失败") }
        }
    }
}
