package com.fyordo.cms.server.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component

@Component
class CmsMetrics(registry: MeterRegistry) {
    // Raft
    val raftApplyTotal: Counter = registry.counter("cms_raft_apply_total")
    val raftApplyErrorTotal: Counter = registry.counter("cms_raft_apply_error_total")
    val raftApplyTimer: Timer = Timer.builder("cms_raft_apply_duration")
        .description("Duration of raft log apply operations")
        .register(registry)
    val raftPutTotal: Counter = registry.counter("cms_raft_put_total")
    val raftDeleteTotal: Counter = registry.counter("cms_raft_delete_total")
    val raftSnapshotTotal: Counter = registry.counter("cms_raft_snapshot_total")
    val raftQueryTotal: Counter = registry.counter("cms_raft_query_total")
    val raftQueryErrorTotal: Counter = registry.counter("cms_raft_query_error_total")

    // Agent gRPC streams
    val agentConnectionsTotal: Counter = registry.counter("cms_agent_connections_total")
    val agentDisconnectionsTotal: Counter = registry.counter("cms_agent_disconnections_total")

    // Property queries
    val propertyGetTotal: Counter = registry.counter("cms_property_get_total")
    val propertyGetNotFoundTotal: Counter = registry.counter("cms_property_get_not_found_total")
    val propertyGetTimer: Timer = Timer.builder("cms_property_get_duration")
        .description("Duration of property get requests")
        .register(registry)
    val propertyQueryTotal: Counter = registry.counter("cms_property_query_total")
    val propertyQueryTimer: Timer = Timer.builder("cms_property_query_duration")
        .description("Duration of property query requests")
        .register(registry)

    companion object {
        fun noOp(): CmsMetrics = CmsMetrics(SimpleMeterRegistry())
    }
}
