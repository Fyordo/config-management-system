package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import org.springframework.stereotype.Component

@Component
class PropertyPathHolder {
    private val namespaces: MutableSet<String> = mutableSetOf()
    private val services: MutableSet<String> = mutableSetOf()
    private val appIds: MutableSet<String> = mutableSetOf()
    private val keys: MutableSet<String> = mutableSetOf()

    fun getNamespaces() = namespaces
    fun getServices() = services
    fun getAppIds() = appIds
    fun getKeys() = keys

    fun addProperty(key: PropertyKey) {
        namespaces.add(key.namespace)
        services.add(key.service)
        appIds.add(key.appId)
        keys.add(key.key)
    }

    fun removeProperty(key: PropertyKey) {
        keys.remove(key.key)
    }
}
