package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.PendingRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PendingRequestRepository : JpaRepository<PendingRequest, UUID> {

    fun findAllByAppGroupId(appGroupId: Long): List<PendingRequest>

    fun findAllByUserId(userId: String): List<PendingRequest>

    fun findAllByApprovedUserIdIsNull(): List<PendingRequest>
}
