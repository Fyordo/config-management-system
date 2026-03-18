package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.UpdateAudit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UpdateAuditRepository : JpaRepository<UpdateAudit, UUID> {

    fun findAllByAppGroupId(appGroupId: Long): List<UpdateAudit>

    fun findAllByUserId(userId: String): List<UpdateAudit>
}
