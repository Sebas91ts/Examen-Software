package com.systembpm.system.modules.document.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.document")
public record DocumentProperties(
        long maxFileSizeBytes,
        List<String> allowedContentTypes,
        Storage storage,
        SignedUrl signedUrl
) {
    public record Storage(S3 s3) {
    }

    public record S3(
            String bucket,
            String region,
            String endpoint,
            boolean pathStyleAccessEnabled,
            String accessKey,
            String secretKey
    ) {
    }

    public record SignedUrl(
            long expirationMinutes
    ) {
    }
}
