package com.fyordo.cms.server.grpc

import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.agent.AgentConnectionFacade
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

class AgentGrpcStreamListener(
    private val agentConnectionFacade: AgentConnectionFacade,
    private val responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>,
    private val scope: CoroutineScope,
    private val sessionId: String,
) : StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> {

    override fun onNext(command: AgentChannelServiceOuterClass.AgentStreamEvent) {
        when {
            command.hasHeartbeat() -> {
                logger.trace { "Heartbeat from session $sessionId" }
            }

            command.hasConnect() -> {
                val agentId = AgentId(
                    command.connect.namespace,
                    command.connect.service,
                    command.connect.appId,
                )
                agentConnectionFacade.register(agentId, responseObserver)
                agentConnectionFacade.sendInitToAgent(agentId)
            }

            else -> {
                logger.warn { "Unknown command from session $sessionId" }
            }
        }
    }

    override fun onError(t: Throwable) {
        logger.warn(t) { "Client error in session $sessionId" }
    }

    override fun onCompleted() {
        logger.info { "Watch session completed: $sessionId" }
        scope.cancel("Session completed")
        try {
            agentConnectionFacade.close()
        } catch (e: Exception) {
            logger.warn(e) { "Error completing response observer" }
        }
    }
}