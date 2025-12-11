package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyInternalDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

private val logger = KotlinLogging.logger {}

@Component
class PropertyInMemoryStorage(
    private val pathHolder: PropertyPathHolder
) {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val storage = mutableMapOf<PropertyKey, PropertyValue>()

    operator fun set(key: PropertyKey, value: PropertyValue) {
        lock.writeLock().lock()
        try {
            storage[key] = value
            pathHolder.addProperty(key)
            logger.debug { "Stored value $key -> $value" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to store value $key -> $value" }
        } finally {
            lock.writeLock().unlock()
        }
    }

    operator fun get(key: PropertyKey): PropertyValue? {
        lock.readLock().lock()
        try {
            return storage[key]
        } catch (e: Exception) {
            logger.error(e) { "Failed to get value by $key" }
            return null
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getByFilter(filter: PropertyQueryFilter): Sequence<PropertyInternalDto> {
        lock.readLock().lock()

        try {
            val namespaces = pathHolder.getNamespaces().filter {
                filter.namespaceRegex?.toRegex()?.matches(it) ?: true
            }
            val services = pathHolder.getServices().filter {
                filter.serviceRegex?.toRegex()?.matches(it) ?: true
            }
            val appIds = pathHolder.getAppIds().filter {
                filter.appIdRegex?.toRegex()?.matches(it) ?: true
            }
            val keys = pathHolder.getKeys().filter {
                filter.keyRegex?.toRegex()?.matches(it) ?: true
            }

            return storage.asSequence().filter {
                namespaces.contains(it.key.namespace) and
                        services.contains(it.key.service) and
                        appIds.contains(it.key.appId) and
                        keys.contains(it.key.key)
            }
                .map { PropertyInternalDto(it.key, it.value) }
                .take(filter.limit)
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getInitForApp(namespace: String, service: String, appId: String): List<PropertyInternalDto> {
        lock.readLock().lock()

        try {
            return storage.filter {
                it.key.namespace == namespace &&
                        it.key.service == service &&
                        it.key.appId == appId
            }
                .map { PropertyInternalDto(it.key, it.value) }
        } finally {
            lock.readLock().unlock()
        }
    }

    fun remove(key: PropertyKey): PropertyValue? {
        lock.writeLock().lock()
        try {
            return storage.remove(key).also {
                pathHolder.removeProperty(key)
                logger.debug { "Removed key $key" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove value by key $key" }
        } finally {
            lock.writeLock().unlock()
        }
        return null
    }
}