package com.birkneo.Aiworks.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val ONBOARDING_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PASSWORD = stringPreferencesKey("app_lock_password")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = doublePreferencesKey("top_p")
        val COMPUTE_ACCELERATOR = stringPreferencesKey("compute_accelerator")
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[MODEL_PATH]
    }

    val temperature: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[TEMPERATURE] ?: 0.8
    }

    val maxTokens: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_TOKENS] ?: 4096
    }

    val topK: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TOP_K] ?: 40
    }

    val topP: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[TOP_P] ?: 0.95
    }

    val computeAccelerator: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[COMPUTE_ACCELERATOR] ?: "GPU"
    }

    val customInstructions: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_INSTRUCTIONS] ?: ""
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }

    val appLockPassword: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_PASSWORD]
    }

    suspend fun setModelPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_PATH] = path
        }
    }

    suspend fun setTemperature(temperature: Double) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temperature
        }
    }

    suspend fun setMaxTokens(maxTokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = maxTokens
        }
    }

    suspend fun setTopK(topK: Int) {
        context.dataStore.edit { preferences ->
            preferences[TOP_K] = topK
        }
    }

    suspend fun setTopP(topP: Double) {
        context.dataStore.edit { preferences ->
            preferences[TOP_P] = topP
        }
    }

    suspend fun setComputeAccelerator(accelerator: String) {
        context.dataStore.edit { preferences ->
            preferences[COMPUTE_ACCELERATOR] = accelerator
        }
    }

    suspend fun setCustomInstructions(instructions: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_INSTRUCTIONS] = instructions
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setAppLockPassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LOCK_PASSWORD] = password
        }
    }
}
