package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cms.grpc.server")
data class GrpcServerConfiguration(
    val port: Int = 9090,
    val address: String = "0.0.0.0"
) {
    fun getServerUrl(): String {
        val host = if (address == "0.0.0.0") "localhost" else address
        return "grpc://$host:$port"
    }
}
