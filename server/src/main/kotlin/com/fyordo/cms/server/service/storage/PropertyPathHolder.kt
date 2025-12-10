package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

@Component
class PropertyPathHolder {
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

    fun addProperty(key: PropertyKey) = modifyUnderLock {
        namespaces.add(key.namespace)
        services.add(key.service)
        appIds.add(key.appId)
        keys.add(key.key)
    }

    fun removeProperty(key: PropertyKey) = modifyUnderLock {
        keys.remove(key.key)
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
