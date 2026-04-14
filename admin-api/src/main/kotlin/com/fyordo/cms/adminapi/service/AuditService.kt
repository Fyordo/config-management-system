package com.fyordo.cms.adminapi.service

import com.fyordo.cms.adminapi.dto.audit.CreateUserAuditDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditFullDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditNoValueDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditSearchFilter
import com.fyordo.cms.adminapi.entity.UserAudit
import com.fyordo.cms.adminapi.repository.UserAuditRepository
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class AuditService(
    private val userAuditRepository: UserAuditRepository
) {
    fun findById(id: UUID): UserAuditFullDto? =
        userAuditRepository.findById(id).map { it.toFullDto() }.orElse(null)

    fun findByFilter(filter: UserAuditSearchFilter, pageable: Pageable): Page<UserAuditNoValueDto> {
        val effectivePageable =
            if (pageable.sort.isSorted) {
                pageable
            } else {
                PageRequest.of(
                    pageable.pageNumber,
                    pageable.pageSize,
                    Sort.by(Sort.Direction.DESC, "timestamp"),
                )
            }
        return userAuditRepository
            .findByFilter(
                filter.namespaceRegex.blankToNull(),
                filter.serviceRegex.blankToNull(),
                filter.appIdRegex.blankToNull(),
                filter.keyRegex.blankToNull(),
                filter.userId.blankToNull(),
                filter.after?.let { Timestamp.from(it) },
                filter.before?.let { Timestamp.from(it) },
                effectivePageable,
            )
            .map { it.toNoValueDto() }
    }

    fun createAudit(auditDto: CreateUserAuditDto): UserAuditFullDto {
        try {
            val saved =
                userAuditRepository.save(
                    UserAudit(
                        userId = auditDto.userId,
                        namespace = auditDto.namespace,
                        service = auditDto.service,
                        appId = auditDto.appId,
                        key = auditDto.key,
                        prevValue = auditDto.prevValue,
                        newValue = auditDto.newValue,
                        timestamp = Timestamp.valueOf(LocalDateTime.now()),
                    ),
                )
            logger.debug { "Successfully saved audit [${saved.id}]" }
            return saved.toFullDto()
        } catch (e: RuntimeException) {
            logger.warn(e) { "Failed to save audit [$auditDto]" }
            throw e
        }
    }

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun UserAudit.toNoValueDto(): UserAuditNoValueDto =
        UserAuditNoValueDto(
            id = id!!,
            userId = userId,
            namespace = namespace,
            service = service,
            appId = appId,
            key = key,
            timestamp = timestamp,
        )

    private fun UserAudit.toFullDto(): UserAuditFullDto =
        UserAuditFullDto(
            id = id!!,
            userId = userId,
            namespace = namespace,
            service = service,
            appId = appId,
            key = key,
            prevValue = prevValue,
            newValue = newValue,
            timestamp = timestamp,
        )
}
