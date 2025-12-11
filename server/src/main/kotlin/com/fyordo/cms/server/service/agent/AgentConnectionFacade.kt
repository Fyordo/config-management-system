package com.fyordo.cms.server.service.agent

import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.google.protobuf.ByteString
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Component
class AgentConnectionFacade(
    private val propertyInMemoryStorage: PropertyInMemoryStorage
) {
    private val connections: MutableMap<AgentId, StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>> =
        ConcurrentHashMap()

    fun register(agentId: AgentId, streamObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>) {
        logger.info { "Registering stream event: $agentId" }
        connections[agentId] = streamObserver
    }
    
    fun unregister(agentId: AgentId) {
        connections.remove(agentId)
        logger.info { "Unregistered agent: $agentId" }
    }

    fun sendToAgent(agentId: AgentId, result: AgentChannelServiceOuterClass.ServerStreamEvent) {
        val streamObserver = connections[agentId]
        if (streamObserver != null) {
            try {
                // Check if the call is cancelled before sending
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
        connections[agentId]?.let { streamObserver ->
            try {
                // Check if the call is cancelled before sending
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
        } ?: run {
            logger.error { "AgentConnectionFacade.sendInitToAgent failed, no stream found for agentId=$agentId" }
        }
    }

    fun closeStream(agentId: AgentId) {
        connections.remove(agentId)?.let {
            logger.info { "Closed connection with agentId: $agentId" }
            try {
                it.onCompleted()
            } catch (e: Exception) {
                logger.warn(e) { "Error completing stream for agent: $agentId" }
            }
        }
    }

    fun close() {
        connections.forEach {
            closeStream(it.key)
        }
    }
}