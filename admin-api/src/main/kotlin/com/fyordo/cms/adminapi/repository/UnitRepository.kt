package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.Unit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UnitRepository : JpaRepository<Unit, UUID>
