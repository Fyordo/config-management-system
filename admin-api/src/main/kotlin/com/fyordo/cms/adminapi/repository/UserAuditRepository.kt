package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.UserAudit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.sql.Timestamp

interface UserAuditRepository : JpaRepository<UserAudit, Long> {

    @Query(
        value = """
            SELECT a.* FROM audit a
            WHERE (CAST(:namespaceRegex AS text) IS NULL OR a.namespace ~ CAST(:namespaceRegex AS text))
              AND (CAST(:serviceRegex AS text) IS NULL OR a.service ~ CAST(:serviceRegex AS text))
              AND (CAST(:appIdRegex AS text) IS NULL OR a.app_id ~ CAST(:appIdRegex AS text))
              AND (CAST(:keyRegex AS text) IS NULL OR a."key" ~ CAST(:keyRegex AS text))
              AND (CAST(:userId AS text) IS NULL OR a.user_id = CAST(:userId AS text))
              AND (CAST(:afterTs AS timestamp) IS NULL OR a.timestamp >= CAST(:afterTs AS timestamp))
              AND (CAST(:beforeTs AS timestamp) IS NULL OR a.timestamp <= CAST(:beforeTs AS timestamp))
        """,
        countQuery = """
            SELECT count(a.id) FROM audit a
            WHERE (CAST(:namespaceRegex AS text) IS NULL OR a.namespace ~ CAST(:namespaceRegex AS text))
              AND (CAST(:serviceRegex AS text) IS NULL OR a.service ~ CAST(:serviceRegex AS text))
              AND (CAST(:appIdRegex AS text) IS NULL OR a.app_id ~ CAST(:appIdRegex AS text))
              AND (CAST(:keyRegex AS text) IS NULL OR a."key" ~ CAST(:keyRegex AS text))
              AND (CAST(:userId AS text) IS NULL OR a.user_id = CAST(:userId AS text))
              AND (CAST(:afterTs AS timestamp) IS NULL OR a.timestamp >= CAST(:afterTs AS timestamp))
              AND (CAST(:beforeTs AS timestamp) IS NULL OR a.timestamp <= CAST(:beforeTs AS timestamp))
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