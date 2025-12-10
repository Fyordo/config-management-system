package com.fyordo.cms.server.rest

import com.fyordo.cms.server.dto.property.PropertyDto
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.property.PropertyValueDto
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.serialization.property.deserializePropertyKey
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.utils.EMPTY_BYTES
import mu.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/v1/property/query")
class PropertyQueryController(
    private val clientFacade: RaftClientFacade
) {
    @GetMapping("/get")
    suspend fun get(@RequestParam key: String): PropertyDto {
        logger.info { "Executing GET query: key=$key" }
        val deserializedKey = deserializePropertyKey(key)
        val query = RaftCommand(
            operation = RaftOp.GET,
            key = deserializedKey,
            value = PropertyValue(1, EMPTY_BYTES, 0L)
        )
        val result = clientFacade.sendQuery(query)
        val deserializedResult = deserializeRaftResult(result)
        return PropertyDto(
            key = PropertyKeyDto(deserializedKey),
            value = PropertyValueDto(
                deserializePropertyValue(deserializedResult.result)
            ),
        )
    }
}