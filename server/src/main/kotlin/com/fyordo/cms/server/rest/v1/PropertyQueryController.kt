package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.config.CmsMetrics
import com.fyordo.cms.server.dto.property.PropertyDto
import com.fyordo.cms.server.dto.property.PropertyKeyDto
import com.fyordo.cms.server.dto.property.PropertyValueDto
import com.fyordo.cms.server.dto.query.ConstantsDto
import com.fyordo.cms.server.dto.query.ConstantsQueryFilter
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
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
    private val metrics: CmsMetrics
) {
    @PostMapping("/get")
    suspend fun get(@Valid @RequestBody key: PropertyKeyDto): PropertyDto {
        val sample = Timer.start()
        metrics.propertyGetTotal.increment()
        try {
            val value: CmsProto.PropertyValue =
                inMemoryStorage[key.toProto()] ?: run {
                    metrics.propertyGetNotFoundTotal.increment()
                    throw ResponseStatusException(HttpStatus.NOT_FOUND)
                }

            return PropertyDto(
                key = key,
                value = PropertyValueDto(value),
            )
        } finally {
            sample.stop(metrics.propertyGetTimer)
        }
    }

    @GetMapping("/constants")
    suspend fun constants(@Valid @RequestBody filter: ConstantsQueryFilter): ConstantsDto {
        return propertyPartsHolder.getConstantsByFilter(filter)
    }

    @PostMapping("")
    suspend fun query(@Valid @RequestBody filter: PropertyQueryFilter): List<PropertyDto> {
        val sample = Timer.start()
        metrics.propertyQueryTotal.increment()
        try {
            return inMemoryStorage.getByFilter(filter).toList()
                .map { PropertyDto(it) }
        } finally {
            sample.stop(metrics.propertyQueryTimer)
        }
    }
}