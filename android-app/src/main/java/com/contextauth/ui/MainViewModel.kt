package com.contextauth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contextauth.core.CollectionCoordinator
import com.contextauth.core.TaskCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = CollectionCoordinator(application)

    val uiState = coordinator.uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        coordinator.uiState.value
    )

    init {
        coordinator.startRuntime(viewModelScope)
    }

    fun refreshPermissions() = coordinator.refreshPermissionState()
    fun syncNow() = coordinator.syncNow(viewModelScope)
    fun grantConsent() = coordinator.grantConsent()
    fun startCollection(category: TaskCategory? = null) = coordinator.startCollection(viewModelScope, category)
    fun stopCollection() = coordinator.stopCollection(viewModelScope)
    fun markTaskComplete(category: TaskCategory) = coordinator.markTaskComplete(category)
    fun updateServerUrl(url: String) = coordinator.updateServerUrl(url, viewModelScope)
    fun resetServerUrl() = coordinator.resetServerUrl(viewModelScope)
    fun testConnection(url: String) = coordinator.testConnection(url, viewModelScope)
    fun checkRules(apply: Boolean = false) = coordinator.checkRules(viewModelScope, apply)
    fun setBatchSeconds(value: Int) = coordinator.setBatchSeconds(value)
    fun setTaskSeconds(value: Int) = coordinator.setTaskSeconds(value)
    fun setAllowThirdParty(value: Boolean) = coordinator.setAllowThirdParty(value)
    fun setWifiOnly(value: Boolean) = coordinator.setWifiOnly(value)
    fun clearQueue() = coordinator.clearQueue()
    fun canStart() = coordinator.canStart()
    fun exportDiagnostics(): String = coordinator.exportDiagnostics()
}
