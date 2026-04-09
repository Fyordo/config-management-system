package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.config.PropertyVersionConfig
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.serialization.raft.raftDeleteCommand
import com.fyordo.cms.server.serialization.raft.raftPutCommand
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.service.raft.RaftServerService
import com.google.protobuf.ByteString
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/v1/property/modify")
class PropertyModificationController(
    private val clientFacade: RaftClientFacade,
    private val versionConfig: PropertyVersionConfig,
    private val raftServerService: RaftServerService,
) {
    @PostMapping("/put")
    suspend fun put(@Valid @RequestBody data: PutPropertyRequest): Map<String, String> {
        val valueBytes = data.value.toByteArray(Charsets.UTF_8)
        if (valueBytes.size > versionConfig.maxValueSizeBytes) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Value size ${valueBytes.size} bytes exceeds maximum ${versionConfig.maxValueSizeBytes} bytes"
            )
        }

        val command = raftPutCommand(
            key = data.key.toProto(),
            valuePayload = CmsProto.PropertyValue.newBuilder()
                .setVersion(versionConfig.currentVersion)
                .setValue(ByteString.copyFrom(valueBytes))
                .setLastModifiedMs(System.currentTimeMillis())
                .build()
                .toByteArray()
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

    @PostMapping("/delete")
    suspend fun delete(@Valid @RequestBody data: PropertyKeyDto): Map<String, CmsProto.RaftResult> {
        val command = raftDeleteCommand(data.toProto())
        return when (val result = clientFacade.sendCommand(command)) {
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
    val key: PropertyKeyDto,
    @field:NotBlank(message = "Value cannot be blank")
    @field:Size(max = 1024 * 1024, message = "Value size cannot exceed 1MB")
    val value: String
)