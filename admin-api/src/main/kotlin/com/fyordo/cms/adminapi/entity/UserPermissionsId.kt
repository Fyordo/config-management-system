package com.fyordo.cms.adminapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class UserPermissionsId(
    @Column(name = "app_group_id", nullable = false)
    val appGroupId: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: String = "",
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserPermissionsId) return false
        return appGroupId == other.appGroupId && userId == other.userId
    }

    override fun hashCode(): Int = 31 * appGroupId.hashCode() + userId.hashCode()
}
