package com.fyordo.cms.server.service.agent

import com.fyordo.cms.AgentChannelServiceOuterClass
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
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

private data class Connection(
    val streamObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>,
    val lock: ReentrantLock = ReentrantLock()
)

@Component
class AgentConnectionManager(
    private val propertyInMemoryStorage: PropertyInMemoryStorage,
    private val broadcaster: PropertyUpdatePublisher,
    private val metrics: CmsMetrics,
    registry: MeterRegistry
) {
    private val connections: MutableMap<AgentId, Connection> = ConcurrentHashMap()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        registry.gaugeMapSize("cms_agent_connected", emptyList(), connections)
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

                val updateEvent = AgentChannelServiceOuterClass.ServerPropertyUpdateEvent.newBuilder()
                    .setProperty(
                        AgentChannelServiceOuterClass.Property.newBuilder()
                            .setKey(event.key.key)
                            .setValue(event.value?.value ?: ByteString.copyFrom(EMPTY_BYTES))
                    )
                    .setLastModifiedMs(event.value?.lastModifiedMs ?: 0)
                    .setRevision(event.revision)
                    .build()

                sendToAgent(
                    agentId,
                    AgentChannelServiceOuterClass.ServerStreamEvent.newBuilder()
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
        return connections.keys.size
    }

    fun register(agentId: AgentId, streamObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>) {
        logger.info { "Registering stream event: $agentId" }
        connections[agentId] = Connection(streamObserver)
        metrics.agentConnectionsTotal.increment()
    }

    fun sendToAgent(agentId: AgentId, result: AgentChannelServiceOuterClass.ServerStreamEvent) {
        connections[agentId]?.let { connection ->
            connection.lock.withLock {
                try {
                    val streamObserver = connection.streamObserver
                    if (streamObserver is ServerCallStreamObserver && streamObserver.isCancelled) {
                        logger.warn { "Stream is cancelled for agent: $agentId, removing connection" }
                        closeStream(agentId)
                        return
                    }
                    streamObserver.onNext(result)
                } catch (e: Exception) {
                    logger.error(e) { "Error sending message to agent: $agentId, removing connection" }
                    closeStream(agentId)
                }
            }
        }
    }

    fun sendInitToAgent(agentId: AgentId) {
        connections[agentId]?.let { connection ->
            connection.lock.withLock {
                try {
                    val streamObserver = connection.streamObserver
                    if (streamObserver is ServerCallStreamObserver && streamObserver.isCancelled) {
                        logger.warn { "Stream is cancelled for agent: $agentId, removing connection" }
                        closeStream(agentId)
                        return
                    }

                    val result = AgentChannelServiceOuterClass.ServerStreamEvent.newBuilder()
                    val properties = AgentChannelServiceOuterClass.ServerInitEvent.newBuilder()
                    val propertiesList = propertyInMemoryStorage.getInitForApp(
                        agentId.namespace,
                        agentId.service,
                        agentId.appId
                    )

                    val lastModifiedMs = propertiesList.fold(0L) { maxTime, property ->
                        val key = property.key
                        val value = property.value
                        properties.addProperties(
                            AgentChannelServiceOuterClass.Property.newBuilder()
                                .setKey(key.key)
                                .setValue(value.value)
                        )
                        maxOf(maxTime, value.lastModifiedMs)
                    }

                    result.setInitEvent(
                        properties
                            .setLastModifiedMs(lastModifiedMs)
                            .setRevision(propertyInMemoryStorage.currentRevision.get())
                            .build()
                    )

                    streamObserver.onNext(result.build())
                    logger.info { "Sent init config to agent: [$agentId] with lastModifiedMs = [$lastModifiedMs]" }
                } catch (e: Exception) {
                    logger.error(e) { "Error sending init config to agent: [$agentId], removing connection" }
                    closeStream(agentId)
                }
            }
        } ?: run {
            logger.error { "AgentConnectionFacade.sendInitToAgent failed, no stream found for agentId=[$agentId]" }
        }
    }

    fun checkAgentRevision(agentId: AgentId, revision: Long) {
        logger.debug { "Received ack from agent: agentId=$agentId, revision=$revision" }

        if (revision < propertyInMemoryStorage.currentRevision.get()) {
            logger.info { "Agent [$agentId] has old revision [$revision], sending init" }
            sendInitToAgent(agentId)
        }
    }

    fun closeStream(agentId: AgentId) {
        connections.remove(agentId)?.let { connection ->
            logger.info { "Closed connection with agentId: $agentId" }
            metrics.agentDisconnectionsTotal.increment()
            connection.lock.withLock {
                try {
                    connection.streamObserver.onCompleted()
                } catch (e: Exception) {
                    logger.warn(e) { "Error completing stream for agent: $agentId" }
                }
            }
        }
    }
}