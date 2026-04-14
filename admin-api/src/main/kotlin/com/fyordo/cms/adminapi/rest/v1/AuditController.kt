package com.fyordo.cms.adminapi.rest.v1

import com.fyordo.cms.adminapi.dto.audit.CreateUserAuditDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditFullDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditNoValueDto
import com.fyordo.cms.adminapi.dto.audit.UserAuditSearchFilter
import com.fyordo.cms.adminapi.service.AuditService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val auditService: AuditService,
) {
    @GetMapping
    fun search(
        @ModelAttribute filter: UserAuditSearchFilter,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<UserAuditNoValueDto> =
        auditService.findByFilter(filter, PageRequest.of(page, size))

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<UserAuditFullDto> {
        val dto = auditService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody @Valid body: CreateUserAuditDto): UserAuditFullDto =
        auditService.createAudit(body)
}
