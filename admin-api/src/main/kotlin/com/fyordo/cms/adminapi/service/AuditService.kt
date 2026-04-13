package com.fyordo.cms.adminapi.service

import com.fyordo.cms.adminapi.dto.audit.CreateUserAuditDto
import com.fyordo.cms.adminapi.entity.UserAudit
import com.fyordo.cms.adminapi.repository.UserAuditRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class AuditService(
    private val userAuditRepository: UserAuditRepository
) {
    fun createAudit(auditDto: CreateUserAuditDto) {
        try {
            val id = UUID.randomUUID()
            userAuditRepository.save(
                UserAudit(
                    id = id,
                    userId = auditDto.userId,
                    namespace = auditDto.namespace,
                    service = auditDto.service,
                    appId = auditDto.appId,
                    key = auditDto.key,
                    prevValue = auditDto.prevValue,
                    newValue = auditDto.newValue,
                    timestamp = Timestamp.valueOf(LocalDateTime.now())
                )
            )
            logger.debug { "Successfully saved audit [$id]" }
        } catch (e: RuntimeException) {
            logger.warn(e) { "Failed to save audit [$auditDto]" }
        }
    }
}
