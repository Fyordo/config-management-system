package com.fyordo.cms.server.grpc.stream

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.CmsEvents
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentChannelGrpcService(
    private val agentConnectionManager: AgentConnectionManager,
) : AgentChannelServiceGrpc.AgentChannelServiceImplBase() {
    override fun watchProperties(
        responseObserver: StreamObserver<CmsEvents.ServerStreamEvent>
    ): StreamObserver<CmsEvents.AgentStreamEvent> {

        val sessionId = UUID.randomUUID().toString()
        val registeredAgent = AtomicReference<AgentId?>(null)

        logger.info { "New bidirectional watch session started: sessionId=$sessionId" }

        return object : StreamObserver<CmsEvents.AgentStreamEvent> {
            override fun onNext(request: CmsEvents.AgentStreamEvent) {
                when {
                    request.hasConnectEvent() -> {
                        val connectEvent = request.connectEvent
                        handleConnectEvent(connectEvent, registeredAgent, sessionId, responseObserver)
                    }

                    request.hasAckEvent() -> {
                        val ackEvent = request.ackEvent
                        handleAckEvent(registeredAgent, sessionId, ackEvent)
                    }

                    else -> {
                        logger.warn { "Received unknown agent event for session [$sessionId]" }
                    }
                }
            }

            override fun onError(t: Throwable) {
                registeredAgent.get()?.let(agentConnectionManager::closeStream)

                logger.warn(t) { "Watch session failed: sessionId=$sessionId" }
            }

            override fun onCompleted() {
                registeredAgent.get()?.let(agentConnectionManager::closeStream)

                logger.info { "Watch session completed: sessionId=$sessionId" }
            }
        }
    }

    private fun handleAckEvent(
        registeredAgent: AtomicReference<AgentId?>,
        sessionId: String,
        ackEvent: CmsEvents.AgentAckEvent
    ) {
        val agentId = registeredAgent.get()
        if (agentId == null) {
            logger.warn { "Ignoring ack before connect for session [$sessionId]" }
            return
        }
        agentConnectionManager.checkAgentConsistency(agentId, ackEvent.failedKeysList)
    }

    private fun handleConnectEvent(
        connectEvent: CmsEvents.AgentConnectEvent,
        registeredAgent: AtomicReference<AgentId?>,
        sessionId: String,
        responseObserver: StreamObserver<CmsEvents.ServerStreamEvent>
    ) {
        val agentId = AgentId(
            connectEvent.namespace,
            connectEvent.service,
            connectEvent.appId
        )
        val previous = registeredAgent.getAndSet(agentId)
        if (previous != null && previous != agentId) {
            logger.warn {
                "Session [$sessionId] changed agent identity from [$previous] to [$agentId], closing previous stream"
            }
            agentConnectionManager.closeStream(previous)
        }
        agentConnectionManager.register(agentId, responseObserver)
        agentConnectionManager.sendInitToAgent(agentId)
        logger.info { "Agent connected to session [$sessionId]: agentId=$agentId" }
    }
}
