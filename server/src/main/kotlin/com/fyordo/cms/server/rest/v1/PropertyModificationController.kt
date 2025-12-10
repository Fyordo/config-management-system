package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.serialization.property.deserializePropertyKey
import com.fyordo.cms.server.serialization.property.serializePropertyKey
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.utils.EMPTY_BYTES
import org.springframework.web.bind.annotation.*

private const val CURRENT_VERSION: Byte = 1

@RestController
@RequestMapping("/v1/property/modify")
class PropertyModificationController(
    private val clientFacade: RaftClientFacade
) {
    @PostMapping("/put")
    suspend fun put(@RequestBody data: PutPropertyRequest): Map<String, String> {
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = data.key,
            value = serializePropertyValue(
                PropertyValue(
                    CURRENT_VERSION,
                    data.value.toByteArray(Charsets.UTF_8),
                    System.currentTimeMillis()
                )
            )
        )
        val result = clientFacade.sendCommand(command)
        val success = deserializeRaftResult(result).status
        return mapOf(
            "result" to success.name,
            "key" to data.key.toString(),
        )
    }

    @DeleteMapping("/delete")
    suspend fun delete(@RequestParam key: String): Map<String, RaftResult> {
        val command = RaftCommand(
            operation = RaftOp.DELETE,
            key = PropertyKey.fromString(key),
            value = serializePropertyValue(
                PropertyValue(
                    CURRENT_VERSION,
                    EMPTY_BYTES,
                    System.currentTimeMillis()
                )
            )
        )
        val result = clientFacade.sendCommand(command)
        return mapOf(
            "result" to deserializeRaftResult(result)
        )
    }
}

data class PutPropertyRequest(
    val key: PropertyKey,
    val value: String
)