package com.fyordo.cms.server.service.agent

import com.fyordo.cms.CmsEvents
import com.fyordo.cms.server.config.CmsMetrics
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.utils.EMPTY_BYTES
import com.google.protobuf.ByteString
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

private data class AgentState(
    val streamObserver: StreamObserver<CmsEvents.ServerStreamEvent>,
    val lock: ReentrantLock = ReentrantLock(),
    var lastAppliedRevision: Long = 0,
    var lastSentRevision: Long = 0
)

@Component
class AgentConnectionManager(
    private val propertyInMemoryStorage: PropertyInMemoryStorage,
    private val broadcaster: PropertyUpdatePublisher,
    private val metrics: CmsMetrics,
    registry: MeterRegistry
) {
    private val agentStates: MutableMap<AgentId, AgentState> = ConcurrentHashMap()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        registry.gaugeMapSize("cms_agent_connected", emptyList(), agentStates)
    }

    @PostConstruct
    fun init() {
        broadcaster.updateFlow
            .onEach { event ->
                val agentId = AgentId(
                    event.key.namespace,
                    event.key.service,
                    event.key.appId
                )

                val modifiedMs = event.value?.lastModifiedMs ?: 0
                val updateEvent = CmsEvents.ServerPropertyUpdateEvent.newBuilder()
                    .setProperty(
                        CmsEvents.Property.newBuilder()
                            .setKey(event.key.key)
                            .setValue(event.value?.value ?: ByteString.copyFrom(EMPTY_BYTES))
                            .setModifiedMs(modifiedMs)
                    )
                    .setRevision(event.revision)
                    .build()

                sendToAgent(
                    agentId,
                    CmsEvents.ServerStreamEvent.newBuilder()
                        .setUpdateEvent(updateEvent)
                        .build()
                )
            }
            .catch { e ->
                logger.error(e) { "Error processing property update" }
            }
            .launchIn(scope)

        logger.info { "AgentConnectionFacade initialized and subscribed to broadcaster" }
    }

    @PreDestroy
    fun destroy() {
        scope.cancel()
        logger.info { "AgentConnectionFacade destroyed" }
    }

    fun getConnectedAgents(): Int {
        return agentStates.keys.size
    }

    fun register(agentId: AgentId, streamObserver: StreamObserver<CmsEvents.ServerStreamEvent>) {
        logger.info { "Registering stream event [$agentId]" }
        agentStates[agentId] = AgentState(streamObserver)
        metrics.agentConnectionsTotal.increment()
    }

    fun sendToAgent(agentId: AgentId, event: CmsEvents.ServerStreamEvent) {
        agentStates[agentId]?.let { agentState ->
            agentState.lock.withLock {
                try {
                    val streamObserver = agentState.streamObserver
                    if (streamObserver is ServerCallStreamObserver && streamObserver.isCancelled) {
                        logger.warn { "Stream is cancelled for agent [$agentId], removing agentState" }
                        closeStream(agentId)
                        return
                    }
                    streamObserver.onNext(event)

                    agentState.lastSentRevision = event.updateEvent.revision
                    logger.debug { "Change lastSentRevision for agent [$agentId] to [${agentState.lastSentRevision}]" }
                } catch (e: Exception) {
                    logger.error(e) { "Error sending message to agent [$agentId], removing agentState" }
                    closeStream(agentId)
                }
            }
        }
    }

    fun sendInitToAgent(agentId: AgentId) {
        val state = agentStates[agentId] ?: run {
            logger.error { "No AgentState found for agentId [$agentId]" }
            return
        }

        state.lock.withLock {
            try {
                val streamObserver = state.streamObserver
                if (streamObserver is ServerCallStreamObserver && streamObserver.isCancelled) {
                    logger.warn { "Stream is cancelled for agent [$agentId], removing connection" }
                    closeStream(agentId)
                    return
                }

                val initEvent = CmsEvents.ServerInitEvent.newBuilder()
                val propertiesList = propertyInMemoryStorage.getInitForApp(
                    agentId.namespace,
                    agentId.service,
                    agentId.appId
                ).map { property ->
                    CmsEvents.Property.newBuilder()
                        .setKey(property.key.key)
                        .setModifiedMs(property.value.lastModifiedMs)
                        .setValue(property.value.value)
                        .build()
                }

                val result = CmsEvents.ServerStreamEvent.newBuilder()
                    .setInitEvent(
                        initEvent
                            .setRevision(propertyInMemoryStorage.currentRevision.get())
                            .addAllProperties(propertiesList)
                            .build()
                    )

                streamObserver.onNext(result.build())

                logger.info { "Sent init config to agent [$agentId] with revision [${propertyInMemoryStorage.currentRevision.get()}]" }

                state.lastSentRevision = propertyInMemoryStorage.currentRevision.get()
            } catch (e: Exception) {
                logger.error(e) { "Error sending init config to agent [$agentId], removing connection" }
                closeStream(agentId)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun checkAgentRevision(agentId: AgentId, revision: Long) {
        logger.debug { "Received ack from agent: agentId=[$agentId], revision=[$revision]" }

        val state = agentStates[agentId]
        if (state == null) {
            logger.error { "No AgentState found for [$agentId]" }
            return
        }

        state.lock.withLock {
            state.lastAppliedRevision = revision

            if (state.lastAppliedRevision < state.lastSentRevision) {
                logger.info {
                    "Agent [$agentId] last applied revision [${state.lastAppliedRevision}] is less" +
                            "then last sent revision [${state.lastSentRevision}], sending init"
                }

                sendInitToAgent(agentId)

                state.lastSentRevision = propertyInMemoryStorage.currentRevision.get()
            }
        }

    }

    fun closeStream(agentId: AgentId) {
        val agentState = agentStates.remove(agentId) ?: run {
            logger.error { "State for agent [$agentId] doesn't exist, skip closing stream" }
            return
        }

        agentState.lock.withLock {
            try {
                agentState.streamObserver.onCompleted()
            } catch (e: Exception) {
                logger.warn(e) { "Error completing stream for agent [$agentId]" }
            }
        }
            .also { logger.info { "Closed connection with agentId [$agentId]" } }
            .also { metrics.agentDisconnectionsTotal.increment() }
    }
}