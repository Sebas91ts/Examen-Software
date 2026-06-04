package com.systembpm.system.modules.document.infrastructure.storage;

import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.domain.DocumentUrlExpiredException;
import com.systembpm.system.modules.document.infrastructure.config.DocumentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentStorageAdapter implements DocumentStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentProperties documentProperties;

    @Override
    public void upload(String key, String contentType, long size, InputStream inputStream) {
        String bucket = documentProperties.storage().s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS S3 bucket no esta configurado");
        }

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
        } catch (S3Exception ex) {
            log.error("Error subiendo archivo a S3. bucket={}, key={}", bucket, key, ex);
            throw new IllegalStateException("No se pudo almacenar el archivo en S3", ex);
        }
    }

    @Override
    public byte[] download(String key) {
        String bucket = documentProperties.storage().s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS S3 bucket no esta configurado");
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (S3Exception ex) {
            log.error("Error descargando archivo desde S3. bucket={}, key={}", bucket, key, ex);
            throw new IllegalStateException("No se pudo descargar el archivo desde S3", ex);
        }
    }

    @Override
    public void delete(String key) {
        String bucket = documentProperties.storage().s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            log.warn("No se pudo revertir el archivo {} en S3", key, ex);
        }
    }

    @Override
    public PresignedDownloadUrl generateDownloadUrl(String key, String downloadFileName, Duration expiration) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new DocumentUrlExpiredException("La configuracion de expiracion de la URL es invalida");
        }

        String bucket = documentProperties.storage().s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS S3 bucket no esta configurado");
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .responseContentDisposition("attachment; filename=\"" + sanitizeFilename(downloadFileName) + "\"")
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return new PresignedDownloadUrl(
                    presignedRequest.url().toString(),
                    Instant.now().plus(expiration)
            );
        } catch (S3Exception ex) {
            log.error("No se pudo generar signed URL para key {}", key, ex);
            throw new IllegalStateException("No se pudo generar la URL de descarga", ex);
        }
    }

    @Override
    public PresignedDownloadUrl generateObjectUrl(String key, Duration expiration) {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new DocumentUrlExpiredException("La configuracion de expiracion de la URL es invalida");
        }

        String bucket = documentProperties.storage().s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS S3 bucket no esta configurado");
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return new PresignedDownloadUrl(
                    presignedRequest.url().toString(),
                    Instant.now().plus(expiration)
            );
        } catch (S3Exception ex) {
            log.error("No se pudo generar signed object URL para key {}", key, ex);
            throw new IllegalStateException("No se pudo generar la URL de objeto", ex);
        }
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName.replace("\"", "");
    }
}
