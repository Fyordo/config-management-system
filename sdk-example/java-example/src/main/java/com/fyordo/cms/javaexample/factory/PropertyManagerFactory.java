package com.fyordo.cms.javaexample.factory;

import com.fyordo.cms.javaexample.props.CmsProperties;
import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepositoryImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropertyManagerFactory {
    @Bean(initMethod = "init")
    public PropertyManager propertyManager(@NotNull CmsProperties cmsProperties) {
        return new PropertyManager(
                new PropertyRepositoryImpl(),
                cmsProperties.getConfigFilePath(),
                cmsProperties.getUnixSocketPath(),
                (k, o, n) -> {
                    System.out.printf("Change property [%s] value from [%s] to [%s]\n", k, o, n);
                }
        );
    }
}
