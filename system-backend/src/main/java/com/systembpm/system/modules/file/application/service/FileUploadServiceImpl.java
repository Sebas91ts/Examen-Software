package com.systembpm.system.modules.file.application.service;

import com.systembpm.system.modules.file.application.dto.FileUploadResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements IFileUploadService {

    private final RestTemplate restTemplate;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Override
    public FileUploadResponseDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes enviar un archivo valido");
        }
        if (cloudName == null || cloudName.isBlank() || apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Cloudinary no esta configurado");
        }

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = buildSignature(timestamp);
            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
                }
            });
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("signature", signature);
            body.add("resource_type", "auto");

            ResponseEntity<Map> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalStateException("Cloudinary no devolvio respuesta");
            }

            return FileUploadResponseDto.builder()
                    .publicId(stringValue(responseBody.get("public_id")))
                    .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo")
                    .secureUrl(stringValue(responseBody.get("secure_url")))
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .resourceType(stringValue(responseBody.get("resource_type")))
                    .build();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Cloudinary rechazo la subida: " + ex.getResponseBodyAsString(), ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo para subirlo", ex);
        }
    }

    private String buildSignature(String timestamp) {
        // Cloudinary expects the signature of sorted params joined with api_secret.
        String payload = "timestamp=" + timestamp + apiSecret;
        return sha1(payload);
    }

    private String sha1(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo calcular la firma de Cloudinary", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
