package com.systembpm.system.modules.document.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(DocumentProperties properties) {
        DocumentProperties.S3 s3 = getS3Properties(properties);
        AwsCredentialsProvider credentialsProvider = getCredentialsProvider(s3);
        Region region = getRegion(s3);

        var builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        if (hasText(s3.endpoint())) {
            builder.endpointOverride(URI.create(s3.endpoint().trim()));
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(DocumentProperties properties) {
        DocumentProperties.S3 s3 = getS3Properties(properties);
        AwsCredentialsProvider credentialsProvider = getCredentialsProvider(s3);
        Region region = getRegion(s3);

        var builder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);

        if (hasText(s3.endpoint())) {
            builder.endpointOverride(URI.create(s3.endpoint().trim()));
        }

        return builder.build();
    }

    private DocumentProperties.S3 getS3Properties(DocumentProperties properties) {
        if (properties == null || properties.storage() == null || properties.storage().s3() == null) {
            throw new IllegalStateException("La configuracion app.document.storage.s3 es obligatoria");
        }
        return properties.storage().s3();
    }

    private AwsCredentialsProvider getCredentialsProvider(DocumentProperties.S3 s3) {
        if (hasText(s3.accessKey()) && hasText(s3.secretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.accessKey().trim(), s3.secretKey().trim())
            );
        }
        return DefaultCredentialsProvider.create();
    }

    private Region getRegion(DocumentProperties.S3 s3) {
        String region = hasText(s3.region()) ? s3.region().trim() : "us-east-1";
        return Region.of(region);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
