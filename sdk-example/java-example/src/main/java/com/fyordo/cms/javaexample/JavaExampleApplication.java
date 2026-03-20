package com.fyordo.cms.javaexample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JavaExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaExampleApplication.class, args);
    }

}
