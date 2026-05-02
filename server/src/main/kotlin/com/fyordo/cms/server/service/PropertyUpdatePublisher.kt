package com.fyordo.cms.server.service

import com.fyordo.cms.CmsDtos
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PropertyUpdatePublisher {

    private val _updateFlow = MutableSharedFlow<PropertyUpdateEvent>(
        replay = 0,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    val updateFlow: SharedFlow<PropertyUpdateEvent> = _updateFlow.asSharedFlow()

    fun publishUpdate(key: CmsDtos.PropertyKey, value: CmsDtos.PropertyValue?, revision: Long) {
        val event = PropertyUpdateEvent(key, value, revision)
        val emitted = _updateFlow.tryEmit(event)

        if (emitted) {
            logger.debug { "Published update for key: $key revision: $revision" }
        } else {
            logger.warn { "Failed to publish update for key: $key revision: $revision (buffer full)" }
        }
    }
}

data class PropertyUpdateEvent(
    val key: CmsDtos.PropertyKey,
    val value: CmsDtos.PropertyValue? = null,
    val revision: Long = 0L
)