package com.fyordo.cms.server.grpc

import com.fyordo.cms.AgentCoordinationServiceGrpc
import com.fyordo.cms.AgentCoordinationServiceOuterClass
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.springframework.grpc.server.service.GrpcService

private val logger = KotlinLogging.logger {}

@GrpcService
class AgentCoordinationGrpcService(
    private val propertyPartsHolder: PropertyPartsHolder,
) : AgentCoordinationServiceGrpc.AgentCoordinationServiceImplBase() {
    override fun selectNode(
        request: AgentCoordinationServiceOuterClass.SelectNodeReq,
        responseObserver: StreamObserver<AgentCoordinationServiceOuterClass.SelectNodeResp>
    ) {
        logger.info {
            "Agent registration: namespace=${request.namespace}, service=${request.service}, appId=${request.appId}"
        }

        propertyPartsHolder.addNamespace(request.namespace)
        propertyPartsHolder.addService(request.service)
        propertyPartsHolder.addAppId(request.appId)

        val response = AgentCoordinationServiceOuterClass.SelectNodeResp.newBuilder()
            .setNodeUrl("grpc://localhost:9090")
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun disconnect(
        request: AgentCoordinationServiceOuterClass.DisconnectReq,
        responseObserver: StreamObserver<AgentCoordinationServiceOuterClass.DisconnectResp>
    ) {
        logger.info {
            "Agent disconnect: namespace=${request.namespace}, service=${request.service}, appId=${request.appId}"
        }
    }
}
