package com.fyordo.cms.javaexample.rest;

import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Objects;

@RestController
@RequestMapping("/")
class PropertyTestController {
    private final PropertyManager propertyManager;

    public PropertyTestController(@NotNull PropertyManager propertyManager) {
        this.propertyManager = Objects.requireNonNull(propertyManager);
        propertyManager.addUpdateCallback("app.java.example", ((key, oldValue, newValue) -> {
            System.out.println("CUSTOM PROPERTY CALLBACK !!!");
        }));
    }

    @GetMapping("/test/property")
    public ResponseEntity<?> getPropertyValue(@RequestParam String key) {
        return propertyManager.get(key);
    }
}
