package com.fyordo.cms.server.rest

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.serialization.deserializePropertyKey
import com.fyordo.cms.server.serialization.serializePropertyKey
import com.fyordo.cms.server.service.raft.RaftClientFacade
import mu.KotlinLogging
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}
private val EMPTY_BYTES = ByteArray(0)

@RestController
@RequestMapping("/api/property")
class PropertyController(
    private val clientFacade: RaftClientFacade
) {
    @PostMapping("/put")
    suspend fun put(@RequestBody data: PutProperty): Map<String, String> {
        logger.info { "Executing PUT command: key=${data.key}, value=${data.value}" }
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = data.key,
            value = data.value.toByteArray(Charsets.UTF_8)
        )
        val result = clientFacade.sendCommand(command)
        return mapOf(
            "result" to result,
            "key" to serializePropertyKey(data.key),
        )
    }

    @GetMapping("/get")
    suspend fun get(@RequestParam key: String): Map<String, String> {
        logger.info { "Executing GET query: key=$key" }
        val query = RaftCommand(
            operation = RaftOp.GET,
            key = deserializePropertyKey(key),
            value = EMPTY_BYTES
        )
        val result = clientFacade.sendQuery(query)
        return mapOf("result" to result)
    }

    @DeleteMapping("/delete")
    suspend fun delete(@RequestParam key: String): Map<String, String> {
        logger.info { "Executing DELETE command: key=$key" }
        val command = RaftCommand(
            operation = RaftOp.DELETE,
            key = deserializePropertyKey(key),
            value = EMPTY_BYTES
        )
        val result = clientFacade.sendCommand(command)
        return mapOf("result" to result)
    }
}

data class PutProperty(
    val key: PropertyKey,
    val value: String
)