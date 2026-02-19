package com.fyordo.cms.server.grpc.stream

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService
import java.util.*

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentChannelGrpcService(
    private val agentConnectionManager: AgentConnectionManager,
) : AgentChannelServiceGrpc.AgentChannelServiceImplBase() {
    override fun watchProperties(
        request: AgentChannelServiceOuterClass.AgentConnectRequest,
        responseObserver: StreamObserver<AgentChannelServiceOuterClass.ServerStreamEvent>
    ) {
        val sessionId = UUID.randomUUID().toString()

        val agentId = AgentId(
            request.namespace,
            request.service,
            request.appId,
        )
        agentConnectionManager.register(agentId, responseObserver)
        agentConnectionManager.sendInitToAgent(agentId)

        logger.info { "New watch session started: $sessionId" }
    }
}
