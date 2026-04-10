package com.fyordo.cms.server.config

import io.grpc.netty.NettyServerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.ServerBuilderCustomizer
import java.util.concurrent.TimeUnit

@Configuration
class GrpcKeepaliveConfig {

    @Bean
    fun grpcKeepaliveCustomizer() = ServerBuilderCustomizer<NettyServerBuilder> { builder ->
        builder
            .permitKeepAliveTime(5, TimeUnit.SECONDS)
            .permitKeepAliveWithoutCalls(true)
    }
}
