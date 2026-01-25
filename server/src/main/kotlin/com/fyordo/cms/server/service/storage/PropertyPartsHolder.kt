package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
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

    fun getNamespaces() = getUnderLock {
        namespaces
    }

    fun getServices() = getUnderLock {
        services
    }

    fun getAppIds() = getUnderLock {
        appIds
    }

    fun getKeys() = getUnderLock {
        keys
    }

    fun addNamespace(namespace: String) = modifyUnderLock {
        namespaces.add(namespace)
    }

    fun addService(service: String) = modifyUnderLock {
        services.add(service)
    }

    fun addAppId(appId: String) = modifyUnderLock {
        appIds.add(appId)
    }

    fun addProperty(key: PropertyKey) = modifyUnderLock {
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
    ) = modifyUnderLock {
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

    private inline fun getUnderLock(supplier: () -> Set<String>): Set<String> {
        lock.readLock().lock()
        try {
            return supplier.invoke()
        } finally {
            lock.readLock().unlock()
        }
    }

    private inline fun modifyUnderLock(modifier: () -> Unit) {
        lock.writeLock().lock()
        try {
            return modifier.invoke()
        } finally {
            lock.writeLock().unlock()
        }
    }
}
