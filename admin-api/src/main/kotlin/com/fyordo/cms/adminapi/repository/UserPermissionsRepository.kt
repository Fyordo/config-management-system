package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.UserPermissions
import com.fyordo.cms.adminapi.entity.UserPermissionsId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserPermissionsRepository : JpaRepository<UserPermissions, UserPermissionsId> {

    fun findAllByIdAppGroupId(appGroupId: Long): List<UserPermissions>

    fun findAllByIdUserId(userId: String): List<UserPermissions>
}
