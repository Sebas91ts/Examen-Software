package com.systembpm.system.modules.file.application.service;

import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.infrastructure.config.DocumentProperties;
import com.systembpm.system.modules.file.application.dto.FileUploadResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyFileStorageService {

    private final DocumentStoragePort documentStoragePort;
    private final DocumentProperties documentProperties;

    public FileUploadResponseDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes enviar un archivo valido");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : "archivo";
        String normalizedContentType = normalizeContentType(file.getContentType());
        String key = buildLegacyKey(originalName);

        try (InputStream inputStream = file.getInputStream()) {
            documentStoragePort.upload(key, normalizedContentType, file.getSize(), inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo para subirlo a S3", ex);
        }

        long expirationMinutes = documentProperties.signedUrl() != null
                ? documentProperties.signedUrl().expirationMinutes()
                : 15L;
        PresignedDownloadUrl presignedUrl = documentStoragePort.generateDownloadUrl(
                key,
                originalName,
                Duration.ofMinutes(Math.max(expirationMinutes, 1))
        );

        log.info("Archivo legacy almacenado en S3 con key {}", key);
        return FileUploadResponseDto.builder()
                .publicId(key)
                .fileName(originalName)
                .secureUrl(presignedUrl.url())
                .mimeType(normalizedContentType)
                .size(file.getSize())
                .resourceType("s3")
                .build();
    }

    private String buildLegacyKey(String originalName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = resolveExtension(originalName);
        String fileName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        return "legacy-files/" + datePath + "/" + fileName;
    }

    private String resolveExtension(String originalName) {
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(index + 1)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.trim().toLowerCase(Locale.ROOT);
    }
}
