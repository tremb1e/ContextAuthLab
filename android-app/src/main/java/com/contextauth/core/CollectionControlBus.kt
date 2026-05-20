package com.contextauth.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CollectionControlBus {
    private val mutableStopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests = mutableStopRequests.asSharedFlow()

    fun requestStop() {
        mutableStopRequests.tryEmit(Unit)
    }
}
