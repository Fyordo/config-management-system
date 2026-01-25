package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.utils.EMPTY_BYTES
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

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
        return when (result) {
            is RaftOperationResult.Success -> {
                val success = deserializeRaftResult(result.data).status
                mapOf(
                    "result" to success.name,
                    "key" to data.key.toString(),
                )
            }

            is RaftOperationResult.Error -> {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to execute command: ${result.message}"
                )
            }
        }
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
        return when (result) {
            is RaftOperationResult.Success -> {
                mapOf(
                    "result" to deserializeRaftResult(result.data)
                )
            }

            is RaftOperationResult.Error -> {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to execute command: ${result.message}"
                )
            }
        }
    }
}

data class PutPropertyRequest(
    val key: PropertyKey,
    val value: String
)