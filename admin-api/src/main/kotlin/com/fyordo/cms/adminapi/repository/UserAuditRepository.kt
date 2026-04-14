package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.UserAudit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.sql.Timestamp
import java.util.UUID

interface UserAuditRepository : JpaRepository<UserAudit, UUID> {

    @Query(
        value = """
            SELECT a.* FROM audit a
            WHERE (:namespaceRegex IS NULL OR a.namespace ~ CAST(:namespaceRegex AS text))
              AND (:serviceRegex IS NULL OR a.service ~ CAST(:serviceRegex AS text))
              AND (:appIdRegex IS NULL OR a.app_id ~ CAST(:appIdRegex AS text))
              AND (:keyRegex IS NULL OR a."key" ~ CAST(:keyRegex AS text))
              AND (:userId IS NULL OR a.user_id = :userId)
              AND (:afterTs IS NULL OR a.timestamp >= :afterTs)
              AND (:beforeTs IS NULL OR a.timestamp <= :beforeTs)
        """,
        countQuery = """
            SELECT count(a.id) FROM audit a
            WHERE (:namespaceRegex IS NULL OR a.namespace ~ CAST(:namespaceRegex AS text))
              AND (:serviceRegex IS NULL OR a.service ~ CAST(:serviceRegex AS text))
              AND (:appIdRegex IS NULL OR a.app_id ~ CAST(:appIdRegex AS text))
              AND (:keyRegex IS NULL OR a."key" ~ CAST(:keyRegex AS text))
              AND (:userId IS NULL OR a.user_id = :userId)
              AND (:afterTs IS NULL OR a.timestamp >= :afterTs)
              AND (:beforeTs IS NULL OR a.timestamp <= :beforeTs)
        """,
        nativeQuery = true,
    )
    fun findByFilter(
        @Param("namespaceRegex") namespaceRegex: String?,
        @Param("serviceRegex") serviceRegex: String?,
        @Param("appIdRegex") appIdRegex: String?,
        @Param("keyRegex") keyRegex: String?,
        @Param("userId") userId: String?,
        @Param("afterTs") afterTs: Timestamp?,
        @Param("beforeTs") beforeTs: Timestamp?,
        pageable: Pageable,
    ): Page<UserAudit>
}