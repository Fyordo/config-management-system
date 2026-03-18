package com.fyordo.cms.adminapi.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "update_audit")
class UpdateAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_group_id", nullable = false)
    var appGroup: AppGroup,

    @Column(name = "user_id", nullable = false)
    var userId: String,

    @Column(name = "approved_user_id")
    var approvedUserId: String? = null,

    @Column(name = "prev_value", columnDefinition = "TEXT")
    var prevValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    var newValue: String? = null,

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now(),
)
