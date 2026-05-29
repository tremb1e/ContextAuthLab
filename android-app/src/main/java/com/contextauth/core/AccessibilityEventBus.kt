package com.contextauth.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

object AccessibilityEventBus {
    private val mutableEvents = MutableSharedFlow<ContextEventSnapshot>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = mutableEvents.asSharedFlow()

    fun emit(event: ContextEventSnapshot) {
        mutableEvents.tryEmit(event)
    }
}

object TouchEventBus {
    private val mutableEvents = MutableSharedFlow<TouchEventSnapshot>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = mutableEvents.asSharedFlow()

    fun emit(event: TouchEventSnapshot) {
        mutableEvents.tryEmit(event)
    }
}

object AccessibilityCollectionGate {
    @Volatile
    var active: Boolean = false
}
