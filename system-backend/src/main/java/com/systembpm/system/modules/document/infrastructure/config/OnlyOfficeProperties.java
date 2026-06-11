package com.systembpm.system.modules.document.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "onlyoffice")
public record OnlyOfficeProperties(
        boolean enabled,
        String documentServerUrl,
        String jwtSecret,
        String callbackUrl
) {
}
