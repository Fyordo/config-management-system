package com.fyordo.cms.server.service.storage

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.query.ConstantsDto
import com.fyordo.cms.server.dto.query.ConstantsQueryFilter
import com.fyordo.cms.server.utils.read
import com.fyordo.cms.server.utils.write
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

@Component
class PropertyPartsHolder {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private val namespaces: MutableSet<String> = mutableSetOf()
    private val services: MutableSet<String> = mutableSetOf()
    private val appIds: MutableSet<String> = mutableSetOf()
    private val keys: MutableSet<String> = mutableSetOf()

    fun getNamespaces(): Set<String> = lock.read { namespaces.toSet() }

    fun getServices(): Set<String> = lock.read { services.toSet() }

    fun getAppIds(): Set<String> = lock.read { appIds.toSet() }

    fun getKeys(): Set<String> = lock.read { keys.toSet() }

    fun getConstantsByFilter(filter: ConstantsQueryFilter) : ConstantsDto {
        lock.read {
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
    }

    fun addProperty(key: CmsProto.PropertyKeyProto) = lock.write {
        namespaces.add(key.namespace)
        services.add(key.service)
        appIds.add(key.appId)
        keys.add(key.key)
    }

    fun removeProperty(
        key: CmsProto.PropertyKeyProto,
        hasOtherWithNamespace: Boolean,
        hasOtherWithService: Boolean,
        hasOtherWithAppId: Boolean,
        hasOtherWithKey: Boolean
    ) = lock.write {
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

    fun clear() = lock.write {
        namespaces.clear()
        services.clear()
        appIds.clear()
        keys.clear()
    }
}
