package com.fyordo.cms.server.service.storage

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.query.ConstantsDto
import com.fyordo.cms.server.dto.query.ConstantsQueryFilter
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PropertyPartsHolder {
    private val namespaces: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val services: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val appIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val keys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun getNamespaces(): Set<String> = namespaces.toSet()

    fun getServices(): Set<String> = services.toSet()

    fun getAppIds(): Set<String> = appIds.toSet()

    fun getKeys(): Set<String> = keys.toSet()

    fun getConstantsByFilter(filter: ConstantsQueryFilter): ConstantsDto {
        val filteredNamespaces = namespaces.filter {
            filter.namespaceRegex?.toRegex()?.matches(it) ?: true
        }
        val filteredServices = services.filter {
            filter.serviceRegex?.toRegex()?.matches(it) ?: true
        }
        val filteredAppIds = appIds.filter {
            filter.appIdRegex?.toRegex()?.matches(it) ?: true
        }

        return ConstantsDto(
            namespaces.filter { filteredNamespaces.contains(it) }.toSet(),
            services.filter { filteredServices.contains(it) }.toSet(),
            appIds.filter { filteredAppIds.contains(it) }.toSet()
        )
    }

    fun addProperty(key: CmsProto.PropertyKey) {
        namespaces.add(key.namespace)
        services.add(key.service)
        appIds.add(key.appId)
        keys.add(key.key)
    }

    fun removeProperty(
        key: CmsProto.PropertyKey,
        hasOtherWithNamespace: Boolean,
        hasOtherWithService: Boolean,
        hasOtherWithAppId: Boolean,
        hasOtherWithKey: Boolean
    ) {
        if (!hasOtherWithKey) {
            keys.remove(key.key)
        }

        if (!hasOtherWithNamespace) {
            namespaces.remove(key.namespace)
        }

        if (!hasOtherWithService) {
            services.remove(key.service)
        }

        if (!hasOtherWithAppId) {
            appIds.remove(key.appId)
        }
    }

    fun clear() {
        namespaces.clear()
        services.clear()
        appIds.clear()
        keys.clear()
    }
}
