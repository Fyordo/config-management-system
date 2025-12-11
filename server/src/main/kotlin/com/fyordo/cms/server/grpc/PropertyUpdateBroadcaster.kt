package com.fyordo.cms.server.grpc

import com.fyordo.cms.server.dto.grpc.PropertyUpdateEvent
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PropertyUpdateBroadcaster {

    private val _updateFlow = MutableSharedFlow<PropertyUpdateEvent>(
        replay = 0,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val updateFlow: SharedFlow<PropertyUpdateEvent> = _updateFlow.asSharedFlow()

    fun publishUpdate(key: PropertyKey, value: PropertyValue?) {
        val event = PropertyUpdateEvent(key, value)
        val emitted = _updateFlow.tryEmit(event)

        if (emitted) {
            logger.debug { "Published update for key: $key" }
        } else {
            logger.warn { "Failed to publish update for key: $key (buffer full)" }
        }
    }
}
