package com.fyordo.cms.server.service.agent

import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.grpc.PropertyUpdateBroadcaster
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.google.protobuf.ByteString
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
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
class AgentConnectionFacade(
    private val propertyInMemoryStorage: PropertyInMemoryStorage,
    private val broadcaster: PropertyUpdateBroadcaster
) {
    private val connections: MutableMap<AgentId, Connection> = ConcurrentHashMap()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                            .setValue(ByteString.copyFrom(event.value?.value ?: ByteArray(0)))
                    )
                    .setLastModifiedMs(event.value?.lastModifiedMs ?: 0)
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

    fun register(agentId: AgentId, streamObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>) {
        logger.info { "Registering stream event: $agentId" }
        connections[agentId] = Connection(streamObserver)
    }

    fun unregister(agentId: AgentId) {
        connections.remove(agentId)
        logger.info { "Unregistered agent: $agentId" }
    }

    fun sendToAgent(agentId: AgentId, result: AgentChannelServiceOuterClass.ServerStreamEvent) {
        val connection = connections[agentId]
        connection?.lock?.withLock {
            try {
                val streamObserver = connection.streamObserver
                if (streamObserver is ServerCallStreamObserver && streamObserver.isCancelled) {
                    logger.warn { "Stream is cancelled for agent: $agentId, removing connection" }
                    unregister(agentId)
                    return
                }
                streamObserver.onNext(result)
            } catch (e: Exception) {
                logger.error(e) { "Error sending message to agent: $agentId, removing connection" }
                unregister(agentId)
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
                        unregister(agentId)
                        return
                    }

                    val result = AgentChannelServiceOuterClass.ServerStreamEvent.newBuilder()
                    val properties = AgentChannelServiceOuterClass.ServerInitEvent.newBuilder()
                    propertyInMemoryStorage.getInitForApp(
                        agentId.namespace,
                        agentId.service,
                        agentId.appId
                    ).forEach {
                        properties.addProperties(
                            AgentChannelServiceOuterClass.Property.newBuilder()
                                .setKey(it.key.toString())
                                .setValue(ByteString.copyFrom(it.value.value))
                        )
                    }
                    result.setInitEvent(properties.build())

                    streamObserver.onNext(result.build())
                    logger.info { "Sent init config to agent: $agentId" }
                } catch (e: Exception) {
                    logger.error(e) { "Error sending init config to agent: $agentId, removing connection" }
                    unregister(agentId)
                }
            }
        } ?: run {
            logger.error { "AgentConnectionFacade.sendInitToAgent failed, no stream found for agentId=$agentId" }
        }
    }

    fun closeStream(agentId: AgentId) {
        connections.remove(agentId)?.let { connection ->
            logger.info { "Closed connection with agentId: $agentId" }
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