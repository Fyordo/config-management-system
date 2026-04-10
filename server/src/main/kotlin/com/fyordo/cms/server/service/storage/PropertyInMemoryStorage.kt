package com.fyordo.cms.server.service.storage

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

@Component
class PropertyInMemoryStorage(
    private val partsHolder: PropertyPartsHolder
) {
    private val storage = ConcurrentHashMap<CmsProto.PropertyKey, CmsProto.PropertyValue>()

    val currentRevision = AtomicLong(0L)

    operator fun set(key: CmsProto.PropertyKey, value: CmsProto.PropertyValue) {
        storage[key] = value
        partsHolder.addProperty(key)
        logger.debug { "Stored value $key -> $value" }
    }

    operator fun get(key: CmsProto.PropertyKey): CmsProto.PropertyValue? = storage[key]

    fun getByFilter(filter: PropertyQueryFilter): Sequence<CmsProto.PropertyInternalDto> {
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

        return storage.asSequence()
            .filter { entry ->
                namespaces.contains(entry.key.namespace) &&
                        services.contains(entry.key.service) &&
                        appIds.contains(entry.key.appId) &&
                        keys.contains(entry.key.key)
            }
            .map { (key, value) ->
                CmsProto.PropertyInternalDto.newBuilder()
                    .setKey(key)
                    .setValue(value)
                    .build()
            }
            .take(filter.limit)
    }

    fun getInitForApp(namespace: String, service: String, appId: String): List<CmsProto.PropertyInternalDto> =
        storage.filter { (key, _) ->
            key.namespace == namespace &&
                    key.service == service &&
                    key.appId == appId
        }
            .map { (key, value) ->
                CmsProto.PropertyInternalDto.newBuilder()
                    .setKey(key)
                    .setValue(value)
                    .build()
            }

    @Synchronized
    fun remove(key: CmsProto.PropertyKey): CmsProto.PropertyValue? {
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

        return removed
    }

    fun setWithRevision(key: CmsProto.PropertyKey, value: CmsProto.PropertyValue, revision: Long) {
        storage[key] = value
        partsHolder.addProperty(key)
        currentRevision.set(revision)
        logger.debug { "Stored value $key -> $value revision=$revision" }
    }

    @Synchronized
    fun removeWithRevision(key: CmsProto.PropertyKey, revision: Long): CmsProto.PropertyValue? {
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

        return removed
    }

    @Synchronized
    fun getSnapshotData(): Pair<Long, List<CmsProto.PropertyInternalDto>> {
        return currentRevision.get() to storage.map { (key, value) ->
            CmsProto.PropertyInternalDto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build()
        }
    }

    @Synchronized
    fun restoreFromSnapshot(entries: List<CmsProto.PropertyInternalDto>, revision: Long) {
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
