package com.fyordo.cms.server.grpc

import com.fyordo.cms.AgentCoordinationServiceGrpc
import com.fyordo.cms.AgentCoordinationServiceOuterClass
import com.fyordo.cms.server.service.agent.AgentConnectionFacade
import com.fyordo.cms.server.service.storage.PropertyPathHolder
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentCoordinationGrpcService(
    private val propertyPathHolder: PropertyPathHolder,
) : AgentCoordinationServiceGrpc.AgentCoordinationServiceImplBase() {
    override fun selectNode(
        request: AgentCoordinationServiceOuterClass.SelectNodeReq,
        responseObserver: StreamObserver<AgentCoordinationServiceOuterClass.SelectNodeResp>
    ) {
        logger.info {
            "Agent registration: namespace=${request.namespace}, " +
                    "service=${request.service}, appId=${request.appId}"
        }

        propertyPathHolder.addNamespace(request.namespace)
        propertyPathHolder.addService(request.service)
        propertyPathHolder.addAppId(request.appId)

        val response = AgentCoordinationServiceOuterClass.SelectNodeResp.newBuilder()
            .setNodeUrl("grpc://localhost:9090")
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}
