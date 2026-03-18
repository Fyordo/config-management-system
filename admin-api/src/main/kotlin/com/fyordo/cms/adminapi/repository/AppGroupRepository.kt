package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.AppGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AppGroupRepository : JpaRepository<AppGroup, Long> {
}
