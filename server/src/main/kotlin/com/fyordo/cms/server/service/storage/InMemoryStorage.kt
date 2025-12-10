package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import mu.KotlinLogging
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

private val logger = KotlinLogging.logger {}

class InMemoryStorage {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val storage = mutableMapOf<PropertyKey, PropertyValue>()

    operator fun set(key: PropertyKey, value: PropertyValue) {
        lock.writeLock().lock()
        try {
            storage[key] = value
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

    fun remove(key: PropertyKey): PropertyValue? {
        lock.writeLock().lock()
        try {
            return storage.remove(key).also {
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