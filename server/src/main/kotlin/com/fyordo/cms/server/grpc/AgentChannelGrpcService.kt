package com.fyordo.cms.server.grpc

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.dto.grpc.PropertyUpdateEvent
import com.fyordo.cms.server.service.agent.AgentConnectionFacade
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService
import java.util.*

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentChannelGrpcService(
    private val broadcaster: PropertyUpdateBroadcaster,
    private val agentConnectionFacade: AgentConnectionFacade,
) : AgentChannelServiceGrpc.AgentChannelServiceImplBase() {

    override fun watchProperties(
        responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>
    ): StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> {

        val sessionId = UUID.randomUUID().toString()
        logger.info { "New watch session started: $sessionId" }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        broadcaster.updateFlow
            .onEach { event -> sendUpdate(event, sessionId) }
            .catch { e ->
                logger.error(e) { "Error in watch session $sessionId" }
                try {
                    responseObserver.onError(e)
                } catch (ex: Exception) {
                    logger.warn(ex) { "Failed to send error to client" }
                }
            }
            .launchIn(scope)

        return observeAgent(sessionId, scope, responseObserver)
    }

    private fun sendUpdate(event: PropertyUpdateEvent, sessionId: String) {
        try {
            val updateEvent = AgentChannelServiceOuterClass.ServerPropertyUpdateEvent.newBuilder()
                .setProperty(
                    AgentChannelServiceOuterClass.Property.newBuilder()
                        .setKey(event.key.toString())
                        .setValue(ByteString.copyFrom(event.value?.value ?: ByteArray(0)))
                )
                .build()

            agentConnectionFacade.sendToAgent(
                AgentId(
                    event.key.namespace,
                    event.key.service,
                    event.key.appId,
                ),
                AgentChannelServiceOuterClass.ServerStreamEvent.newBuilder()
                    .setUpdateEvent(updateEvent)
                    .build()
            )

            logger.debug { "Sent update to session $sessionId: ${event.key}" }
        } catch (e: Exception) {
            logger.error(e) { "Error sending update to session $sessionId" }
        }
    }

    private fun observeAgent(
        sessionId: String,
        scope: CoroutineScope,
        responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>,
    ): StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> =
        AgentGrpcStreamListener(
            agentConnectionFacade,
            responseObserver,
            scope,
            sessionId
        )
}
