package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.property.PropertyValueDto
import com.fyordo.cms.server.dto.query.ConstantsDto
import com.fyordo.cms.server.dto.query.ConstantsQueryFilter
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.deserializePropertyInternalDtoList
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.serialization.raft.raftGetCommand
import com.fyordo.cms.server.serialization.raft.raftQueryCommand
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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
    suspend fun get(@NotBlank @RequestParam key: String): PropertyDto {
        val deserializedKey = PropertyKey.fromString(key)
        val query = raftGetCommand(deserializedKey)
        val result = clientFacade.sendQuery(query)
        return when (result) {
            is RaftOperationResult.Success -> {
                val deserializedResult = deserializeRaftResult(result.data)
                when (deserializedResult.status) {
                    CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_OK -> PropertyDto(
                        key = PropertyKeyDto(deserializedKey),
                        value = PropertyValueDto(
                            deserializePropertyValue(deserializedResult.result.toByteArray())
                        ),
                    )

                    CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_ERROR -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
                    CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
                    CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_UNSPECIFIED,
                    CmsProto.RaftResultStatus.UNRECOGNIZED -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
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
    suspend fun constants(@Valid @RequestBody filter: ConstantsQueryFilter): ConstantsDto {
        return propertyPartsHolder.getConstantsByFilter(filter)
    }

    @PostMapping("")
    suspend fun query(@Valid @RequestBody filter: PropertyQueryFilter): List<PropertyDto> {
        val query = raftQueryCommand(filter)
        val result = clientFacade.sendQuery(query)
        return when (result) {
            is RaftOperationResult.Success -> {
                val deserializedResult = deserializeRaftResult(result.data)
                deserializePropertyInternalDtoList(deserializedResult.result.toByteArray()).map { PropertyDto(it) }
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