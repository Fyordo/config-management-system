package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
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

    fun addNamespace(namespace: String) = lock.write {
        namespaces.add(namespace)
    }

    fun addService(service: String) = lock.write {
        services.add(service)
    }

    fun addAppId(appId: String) = lock.write {
        appIds.add(appId)
    }

    fun addProperty(key: PropertyKey) = lock.write {
        namespaces.add(key.namespace)
        services.add(key.service)
        appIds.add(key.appId)
        keys.add(key.key)
    }

    fun removeProperty(
        key: PropertyKey,
        hasOtherWithNamespace: Boolean,
        hasOtherWithService: Boolean,
        hasOtherWithAppId: Boolean
    ) = lock.write {
        keys.remove(key.key)

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
