package com.contextauth.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AccessibilityEventBus {
    private val mutableEvents = MutableSharedFlow<ContextEventSnapshot>(extraBufferCapacity = 128)
    val events = mutableEvents.asSharedFlow()

    fun emit(event: ContextEventSnapshot) {
        mutableEvents.tryEmit(event)
    }
}
