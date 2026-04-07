package com.fyordo.cms.javaexample.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@RestController
@RequestMapping("/")
class PropertyTestController {
    private final PropertyManager propertyManager;
    private volatile ExampleClass exampleClass = null;

    public PropertyTestController(@NotNull PropertyManager propertyManager) {
        this.propertyManager = Objects.requireNonNull(propertyManager);
        ObjectMapper objectMapper = new ObjectMapper();

        propertyManager.addUpdateCallback("app.java.example", ((key, oldValue, newValue) -> {
            System.out.println("CUSTOM PROPERTY CALLBACK !!!");
        }));

        propertyManager.addUpdateCallback("app.java.example.exampleClass", ((key, oldValue, newValue) -> {
            exampleClass = objectMapper.convertValue(newValue, ExampleClass.class);
        }));
    }

    @GetMapping("/test/property")
    public ResponseEntity<String> getPropertyValue(@RequestParam String key) {
        return ResponseEntity.ofNullable(
                propertyManager.get(key)
        );
    }

    @GetMapping("/test/exampleClass")
    public ResponseEntity<ExampleClass> getExampleClass() {
        return ResponseEntity.ofNullable(exampleClass);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExampleClass(
            @JsonProperty("intField")
            int intField,
            @JsonProperty("doubleField")
            double doubleField,
            @JsonProperty("stringField")
            String stringField
    ){
        @JsonCreator

        public ExampleClass {
        }
    }
}
