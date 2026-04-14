package com.fyordo.cms.server.grpc.stream

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentChannelGrpcService(
    private val agentConnectionManager: AgentConnectionManager,
) : AgentChannelServiceGrpc.AgentChannelServiceImplBase() {
    override fun watchProperties(responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>):
        StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> {
        val sessionId = UUID.randomUUID().toString()
        val registeredAgent = AtomicReference<AgentId?>(null)

        logger.info { "New bidirectional watch session started: sessionId=$sessionId" }

        return object : StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> {
            override fun onNext(request: AgentChannelServiceOuterClass.AgentStreamEvent) {
                when {
                    request.hasConnectEvent() -> {
                        val connect = request.connectEvent
                        val agentId = AgentId(
                            connect.namespace,
                            connect.service,
                            connect.appId
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

                    request.hasAckEvent() -> {
                        val agentId = registeredAgent.get()
                        if (agentId == null) {
                            logger.warn { "Ignoring ack before connect for session [$sessionId]" }
                            return
                        }
                        agentConnectionManager.checkAgentRevision(agentId, request.ackEvent.revision)
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
}
