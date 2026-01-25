package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.server.dto.property.PropertyDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.property.PropertyValueDto
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import com.fyordo.cms.server.serialization.deserializeList
import com.fyordo.cms.server.serialization.property.deserializePropertyInternalDto
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.query.serializePropertyQueryFilter
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
import com.fyordo.cms.server.utils.EMPTY_BYTES
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/v1/property/query")
class PropertyQueryController(
    private val clientFacade: RaftClientFacade,
    private val propertyPartsHolder: PropertyPartsHolder
) {
    @GetMapping("/get")
    suspend fun get(@RequestParam key: String): PropertyDto {
        val deserializedKey = PropertyKey.fromString(key)
        val query = RaftCommand(
            operation = RaftOp.GET,
            key = deserializedKey,
            value = EMPTY_BYTES
        )
        val result = clientFacade.sendQuery(query)
        return when (result) {
            is RaftOperationResult.Success -> {
                val deserializedResult = deserializeRaftResult(result.data)
                when (deserializedResult.status) {
                    RaftResultStatus.OK -> PropertyDto(
                        key = PropertyKeyDto(deserializedKey),
                        value = PropertyValueDto(
                            deserializePropertyValue(deserializedResult.result)
                        ),
                    )
                    RaftResultStatus.ERROR -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
                    RaftResultStatus.NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
                }
            }
            is RaftOperationResult.Error -> {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to execute query: ${result.message}"
                )
            }
        }
    }

    @GetMapping("/constants")
    suspend fun constants(): Map<String, Set<String>> {
        return mapOf(
            "namespaces" to propertyPartsHolder.getNamespaces(),
            "services" to propertyPartsHolder.getServices(),
            "keys" to propertyPartsHolder.getKeys(),
            "appIds" to propertyPartsHolder.getAppIds(),
        )
    }

    @PostMapping("")
    suspend fun query(@RequestBody filter: PropertyQueryFilter): List<PropertyDto> {
        val filterBytes = serializePropertyQueryFilter(filter)
        val query = RaftCommand(
            operation = RaftOp.QUERY,
            key = null,
            value = filterBytes
        )
        val result = clientFacade.sendQuery(query)
        return when (result) {
            is RaftOperationResult.Success -> {
                val deserializedResult = deserializeRaftResult(result.data)
                deserializeList(
                    deserializedResult.result,
                    ::deserializePropertyInternalDto
                ).map { PropertyDto(it) }
            }
            is RaftOperationResult.Error -> {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to execute query: ${result.message}"
                )
            }
        }
    }
}