package com.fyordo.cms.server.grpc

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.service.agent.AgentConnectionFacade
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService
import java.util.*

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentChannelGrpcService(
    private val agentConnectionFacade: AgentConnectionFacade,
) : AgentChannelServiceGrpc.AgentChannelServiceImplBase() {

    override fun watchProperties(
        responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>
    ): StreamObserver<AgentChannelServiceOuterClass.AgentStreamEvent> {

        val sessionId = UUID.randomUUID().toString()
        logger.info { "New watch session started: $sessionId" }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        return observeAgent(sessionId, scope, responseObserver)
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
