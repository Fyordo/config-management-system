package com.fyordo.cms.javaexample.props;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "cms.init")
public class CmsProperties {
    private final String configFilePath;
    private final String unixSocketPath;

    public CmsProperties(@NotNull String configFilePath, @NotNull String unixSocketPath) {
        this.configFilePath = Objects.requireNonNull(configFilePath);
        this.unixSocketPath = Objects.requireNonNull(unixSocketPath);
    }

    @NotNull
    public String getConfigFilePath() {
        return configFilePath;
    }

    @NotNull
    public String getUnixSocketPath() {
        return unixSocketPath;
    }
}
