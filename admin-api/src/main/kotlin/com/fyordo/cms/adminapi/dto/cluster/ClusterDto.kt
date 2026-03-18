package com.fyordo.cms.adminapi.dto.cluster

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClusterDto(
    val id: UUID,
    var title: String,
    var color: String,
    var raftAddress: String,
)
