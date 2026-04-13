package com.fyordo.cms.adminapi.repository

import com.fyordo.cms.adminapi.entity.Cluster
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClusterRepository : JpaRepository<Cluster, UUID>
