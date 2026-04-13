package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.UserAudit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserAuditRepository : JpaRepository<UserAudit, UUID> {
}