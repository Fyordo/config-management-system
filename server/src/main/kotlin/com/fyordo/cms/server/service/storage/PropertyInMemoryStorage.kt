package com.fyordo.cms.server.service.storage

import com.fyordo.cms.CmsProto
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
    private val storage = mutableMapOf<CmsProto.PropertyKeyProto, CmsProto.PropertyValueProto>()

    val currentRevision = AtomicLong(0L)

    operator fun set(key: CmsProto.PropertyKeyProto, value: CmsProto.PropertyValueProto) = lock.write {
        storage[key] = value
        partsHolder.addProperty(key)
        logger.debug { "Stored value $key -> $value" }
    }

    operator fun get(key: CmsProto.PropertyKeyProto): CmsProto.PropertyValueProto? = lock.read {
        storage[key]
    }

    fun getByFilter(filter: PropertyQueryFilter): Sequence<CmsProto.PropertyInternalDtoProto> = lock.read {
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
            .filter { entry ->
                namespaces.contains(entry.key.namespace) &&
                        services.contains(entry.key.service) &&
                        appIds.contains(entry.key.appId) &&
                        keys.contains(entry.key.key)
            }
            .map { (key, value) ->
                CmsProto.PropertyInternalDtoProto.newBuilder()
                    .setKey(key)
                    .setValue(value)
                    .build()
            }
            .take(filter.limit)
    }

    fun getInitForApp(namespace: String, service: String, appId: String): List<CmsProto.PropertyInternalDtoProto> =
        lock.read {
            storage.filter { (key, _) ->
                key.namespace == namespace &&
                        key.service == service &&
                        key.appId == appId
            }
                .map { (key, value) ->
                    CmsProto.PropertyInternalDtoProto.newBuilder()
                        .setKey(key)
                        .setValue(value)
                        .build()
                }
        }

    fun remove(key: CmsProto.PropertyKeyProto): CmsProto.PropertyValueProto? = lock.write {
        val removed = storage.remove(key)

        if (removed != null) {
            val hasOtherWithNamespace = storage.keys.any { it.namespace == key.namespace }
            val hasOtherWithService = storage.keys.any { it.service == key.service }
            val hasOtherWithAppId = storage.keys.any { it.appId == key.appId }
            val hasOtherWithKey = storage.keys.any { it.key == key.key }

            partsHolder.removeProperty(
                key,
                hasOtherWithNamespace,
                hasOtherWithService,
                hasOtherWithAppId,
                hasOtherWithKey
            )
            logger.debug { "Removed key $key" }
        }

        removed
    }

    fun setWithRevision(key: CmsProto.PropertyKeyProto, value: CmsProto.PropertyValueProto, revision: Long) = lock.write {
        storage[key] = value
        partsHolder.addProperty(key)
        currentRevision.set(revision)
        logger.debug { "Stored value $key -> $value revision=$revision" }
    }

    fun removeWithRevision(key: CmsProto.PropertyKeyProto, revision: Long): CmsProto.PropertyValueProto? = lock.write {
        val removed = storage.remove(key)

        if (removed != null) {
            val hasOtherWithNamespace = storage.keys.any { it.namespace == key.namespace }
            val hasOtherWithService = storage.keys.any { it.service == key.service }
            val hasOtherWithAppId = storage.keys.any { it.appId == key.appId }
            val hasOtherWithKey = storage.keys.any { it.key == key.key }

            partsHolder.removeProperty(
                key,
                hasOtherWithNamespace,
                hasOtherWithService,
                hasOtherWithAppId,
                hasOtherWithKey
            )
            currentRevision.set(revision)
            logger.debug { "Removed key $key revision=$revision" }
        }

        removed
    }

    fun getSnapshotData(): Pair<Long, List<CmsProto.PropertyInternalDtoProto>> = lock.read {
        currentRevision.get() to storage.map { (key, value) ->
            CmsProto.PropertyInternalDtoProto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build()
        }
    }

    fun restoreFromSnapshot(entries: List<CmsProto.PropertyInternalDtoProto>, revision: Long) = lock.write {
        storage.clear()
        partsHolder.clear()
        entries.forEach { entry ->
            storage[entry.key] = entry.value
            partsHolder.addProperty(entry.key)
        }
        currentRevision.set(revision)
        logger.info { "Storage restored from snapshot: ${entries.size} entries, revision=$revision" }
    }
}