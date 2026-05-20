package com.contextauth.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("contextauthlab_settings", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun setConsent(granted: Boolean) = update { copy(consentGranted = granted) }
    fun setServerUrl(url: String) = update { copy(serverUrl = url, serverOverridden = url != DEFAULT_SERVER_URL) }
    fun resetServerUrl() = update { copy(serverUrl = DEFAULT_SERVER_URL, serverOverridden = false) }
    fun setServerStudySalt(value: String) = update { copy(serverStudySalt = value.ifBlank { SERVER_STUDY_SALT }) }
    fun setBatchSeconds(@Suppress("UNUSED_PARAMETER") value: Int) = update { copy(batchSeconds = FIXED_BATCH_SECONDS) }
    fun setTaskSeconds(@Suppress("UNUSED_PARAMETER") value: Int) = update { copy(taskSeconds = FIXED_TASK_SECONDS) }
    fun setAllowThirdParty(@Suppress("UNUSED_PARAMETER") value: Boolean) = update { copy(allowThirdParty = true) }
    fun setWifiOnly(value: Boolean) = update { copy(wifiOnly = value) }
    fun setRule(version: String, hash: String) = update { copy(ruleVersion = version, ruleHash = hash) }

    private fun update(block: AppSettings.() -> AppSettings) {
        val next = mutableSettings.value.block()
        prefs.edit()
            .putBoolean("consent", next.consentGranted)
            .putString("server_url", next.serverUrl)
            .putString("server_study_salt", next.serverStudySalt)
            .putBoolean("server_overridden", next.serverOverridden)
            .putInt("batch_seconds", next.batchSeconds)
            .putInt("task_seconds", next.taskSeconds)
            .putBoolean("allow_third_party", next.allowThirdParty)
            .putBoolean("wifi_only", next.wifiOnly)
            .putString("rule_version", next.ruleVersion)
            .putString("rule_hash", next.ruleHash)
            .apply()
        mutableSettings.value = next
    }

    private fun read(): AppSettings = AppSettings(
        consentGranted = prefs.getBoolean("consent", false),
        serverUrl = prefs.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL,
        serverStudySalt = prefs.getString("server_study_salt", SERVER_STUDY_SALT) ?: SERVER_STUDY_SALT,
        serverOverridden = prefs.getBoolean("server_overridden", false),
        batchSeconds = FIXED_BATCH_SECONDS,
        taskSeconds = FIXED_TASK_SECONDS,
        allowThirdParty = true,
        wifiOnly = prefs.getBoolean("wifi_only", true),
        ruleVersion = prefs.getString("rule_version", "1") ?: "1",
        ruleHash = prefs.getString("rule_hash", "0".repeat(64)) ?: "0".repeat(64)
    )

    private companion object {
        const val FIXED_BATCH_SECONDS = 5
        const val FIXED_TASK_SECONDS = 30
    }
}
