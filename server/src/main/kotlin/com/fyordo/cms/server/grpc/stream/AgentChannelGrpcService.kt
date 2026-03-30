package com.fyordo.cms.server.grpc.stream

import com.fyordo.cms.AgentChannelServiceGrpc
import com.fyordo.cms.AgentChannelServiceOuterClass
import com.fyordo.cms.server.dto.grpc.AgentId
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import com.google.common.util.concurrent.MoreExecutors
import io.grpc.Context
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
        val currentContext: Context = Context.current()

        val agentId = AgentId(
            request.namespace,
            request.service,
            request.appId,
        )
        val cancellationListener: Context.CancellationListener = Context.CancellationListener { context ->
            if (context.isCancelled) {
                agentConnectionManager.closeStream(agentId)
            }
        }

        currentContext.addListener(cancellationListener, MoreExecutors.directExecutor())

        val sessionId = UUID.randomUUID().toString()

        agentConnectionManager.register(agentId, responseObserver)
        agentConnectionManager.sendInitToAgent(agentId)

        logger.info { "New watch session started: sessionId=$sessionId, agentId=$agentId" }
    }
}
