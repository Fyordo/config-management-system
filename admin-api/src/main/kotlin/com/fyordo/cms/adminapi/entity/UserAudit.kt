package com.fyordo.cms.adminapi.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.util.UUID

@Entity
@Table(name = "audit")
class UserAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var userId: String,

    @Column(nullable = false)
    var namespace: String,

    @Column(nullable = false)
    var service: String,

    @Column(nullable = false)
    var appId: String,

    @Column(nullable = false)
    var key: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var prevValue: String?,

    @Column(nullable = false, columnDefinition = "TEXT")
    var newValue: String?,

    @Column(nullable = false)
    var timestamp: Timestamp,
)
