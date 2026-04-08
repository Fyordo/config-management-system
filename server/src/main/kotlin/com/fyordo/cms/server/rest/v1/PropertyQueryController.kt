package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyDto
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.property.PropertyValueDto
import com.fyordo.cms.server.dto.query.ConstantsDto
import com.fyordo.cms.server.dto.query.ConstantsQueryFilter
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/v1/property/query")
class PropertyQueryController(
    private val inMemoryStorage: PropertyInMemoryStorage,
    private val propertyPartsHolder: PropertyPartsHolder,
    private val meterRegistry: MeterRegistry
) {
    private val getTotal = meterRegistry.counter("cms_property_get_total")
    private val getNotFoundTotal = meterRegistry.counter("cms_property_get_not_found_total")
    private val getTimer = Timer.builder("cms_property_get_duration")
        .description("Duration of property get requests")
        .register(meterRegistry)
    private val queryTotal = meterRegistry.counter("cms_property_query_total")
    private val queryTimer = Timer.builder("cms_property_query_duration")
        .description("Duration of property query requests")
        .register(meterRegistry)

    @PostMapping("/get")
    suspend fun get(@Valid @RequestBody key: PropertyKeyDto): PropertyDto {
        val sample = Timer.start(meterRegistry)
        getTotal.increment()
        try {
            val value: CmsProto.PropertyValue =
                inMemoryStorage[key.toProto()] ?: run {
                    getNotFoundTotal.increment()
                    throw ResponseStatusException(HttpStatus.NOT_FOUND)
                }

            return PropertyDto(
                key = key,
                value = PropertyValueDto(value),
            )
        } finally {
            sample.stop(getTimer)
        }
    }

    @GetMapping("/constants")
    suspend fun constants(@Valid @RequestBody filter: ConstantsQueryFilter): ConstantsDto {
        return propertyPartsHolder.getConstantsByFilter(filter)
    }

    @PostMapping("")
    suspend fun query(@Valid @RequestBody filter: PropertyQueryFilter): List<PropertyDto> {
        val sample = Timer.start(meterRegistry)
        queryTotal.increment()
        try {
            return inMemoryStorage.getByFilter(filter).toList()
                .map { PropertyDto(it) }
        } finally {
            sample.stop(queryTimer)
        }
    }
}