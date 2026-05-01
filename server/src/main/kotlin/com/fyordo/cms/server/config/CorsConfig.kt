package com.fyordo.cms.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "app.server.web")
data class CorsProperties(
    val noCorsUrls: List<String> = emptyList()
)

@Configuration
class CorsConfig(private val corsProperties: CorsProperties) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.noCorsUrls
            allowedMethods = listOf("*")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)

        return CorsWebFilter(source)
    }
}
