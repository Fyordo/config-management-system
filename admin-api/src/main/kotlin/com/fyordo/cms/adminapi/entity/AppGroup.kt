package com.fyordo.cms.adminapi.entity

import jakarta.persistence.*

@Entity
@Table(name = "app_group")
class AppGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_id", nullable = false)
    var cluster: Cluster,

    @Column(nullable = false)
    var title: String,

    @Column(name = "namespace_regex", nullable = false)
    var namespaceRegex: String,

    @Column(name = "service_regex", nullable = false)
    var serviceRegex: String,

    @Column(name = "app_id_regex", nullable = false)
    var appIdRegex: String,

    @OneToMany(mappedBy = "appGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val updateAudits: MutableList<UpdateAudit> = mutableListOf(),

    @OneToMany(mappedBy = "appGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val pendingRequests: MutableList<PendingRequest> = mutableListOf(),

    @OneToMany(mappedBy = "appGroup", cascade = [CascadeType.ALL], orphanRemoval = true)
    val userPermissions: MutableList<UserPermissions> = mutableListOf(),
)
