package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyInternalDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.utils.read
import com.fyordo.cms.server.utils.write
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

private val logger = KotlinLogging.logger {}

@Component
class PropertyInMemoryStorage(
    private val partsHolder: PropertyPartsHolder
) {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val storage = mutableMapOf<PropertyKey, PropertyValue>()

    val currentRevision = AtomicLong(0L)

    operator fun set(key: PropertyKey, value: PropertyValue) = lock.write {
        storage[key] = value
        partsHolder.addProperty(key)
        logger.debug { "Stored value $key -> $value" }
    }

    operator fun get(key: PropertyKey): PropertyValue? = lock.read {
        storage[key]
    }

    fun getByFilter(filter: PropertyQueryFilter): Sequence<PropertyInternalDto> = lock.read {
        val namespaces = partsHolder.getNamespaces().filter {
            filter.namespaceRegex?.toRegex()?.matches(it) ?: true
        }
        val services = partsHolder.getServices().filter {
            filter.serviceRegex?.toRegex()?.matches(it) ?: true
        }
        val appIds = partsHolder.getAppIds().filter {
            filter.appIdRegex?.toRegex()?.matches(it) ?: true
        }
        val keys = partsHolder.getKeys().filter {
            filter.keyRegex?.toRegex()?.matches(it) ?: true
        }

        storage.asSequence()
            .filter { (key, _) ->
                namespaces.contains(key.namespace) &&
                        services.contains(key.service) &&
                        appIds.contains(key.appId) &&
                        keys.contains(key.key)
            }
            .map { (key, value) -> PropertyInternalDto(key, value) }
            .take(filter.limit)
    }

    fun getInitForApp(namespace: String, service: String, appId: String): List<PropertyInternalDto> = lock.read {
        storage.filter { (key, _) ->
            key.namespace == namespace &&
                    key.service == service &&
                    key.appId == appId
        }
            .map { (key, value) -> PropertyInternalDto(key, value) }
    }

    fun remove(key: PropertyKey): PropertyValue? = lock.write {
        val removed = storage.remove(key)
        
        if (removed != null) {
            val hasOtherWithNamespace = storage.keys.any { it.namespace == key.namespace }
            val hasOtherWithService = storage.keys.any { it.service == key.service }
            val hasOtherWithAppId = storage.keys.any { it.appId == key.appId }

            partsHolder.removeProperty(
                key,
                hasOtherWithNamespace,
                hasOtherWithService,
                hasOtherWithAppId
            )
            logger.debug { "Removed key $key" }
        }
        
        removed
    }

    fun setWithRevision(key: PropertyKey, value: PropertyValue, revision: Long) = lock.write {
        storage[key] = value
        partsHolder.addProperty(key)
        currentRevision.set(revision)
        logger.debug { "Stored value $key -> $value revision=$revision" }
    }

    fun removeWithRevision(key: PropertyKey, revision: Long): PropertyValue? = lock.write {
        val removed = storage.remove(key)

        if (removed != null) {
            val hasOtherWithNamespace = storage.keys.any { it.namespace == key.namespace }
            val hasOtherWithService = storage.keys.any { it.service == key.service }
            val hasOtherWithAppId = storage.keys.any { it.appId == key.appId }

            partsHolder.removeProperty(
                key,
                hasOtherWithNamespace,
                hasOtherWithService,
                hasOtherWithAppId
            )
            currentRevision.set(revision)
            logger.debug { "Removed key $key revision=$revision" }
        }

        removed
    }

    fun getSnapshotData(): Pair<Long, List<PropertyInternalDto>> = lock.read {
        currentRevision.get() to storage.map { (key, value) -> PropertyInternalDto(key, value) }
    }

    fun restoreFromSnapshot(entries: List<PropertyInternalDto>, revision: Long) = lock.write {
        storage.clear()
        partsHolder.clear()
        entries.forEach { (key, value) ->
            storage[key] = value
            partsHolder.addProperty(key)
        }
        currentRevision.set(revision)
        logger.info { "Storage restored from snapshot: ${entries.size} entries, revision=$revision" }
    }
}