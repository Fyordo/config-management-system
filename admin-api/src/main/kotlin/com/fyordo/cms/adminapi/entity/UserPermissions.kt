package com.fyordo.cms.adminapi.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_permissions")
class UserPermissions(
    @EmbeddedId
    val id: UserPermissionsId,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("appGroupId")
    @JoinColumn(name = "app_group_id", nullable = false)
    var appGroup: AppGroup,

    @Column(nullable = false)
    var read: Boolean = false,

    @Column(nullable = false)
    var edit: Boolean = false,
)
