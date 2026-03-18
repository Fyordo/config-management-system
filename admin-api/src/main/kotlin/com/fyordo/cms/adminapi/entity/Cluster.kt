package com.fyordo.cms.adminapi.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "cluster")
class Cluster(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var color: String,

    @Column(name = "raft_address", nullable = false)
    var raftAddress: String,

    @OneToMany(mappedBy = "cluster", cascade = [CascadeType.ALL], orphanRemoval = true)
    val appGroups: MutableList<AppGroup> = mutableListOf(),
)
