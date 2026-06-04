package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackRequestDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackResponseDto;
import com.systembpm.system.modules.document.application.service.OnlyOfficeIntegrationService;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.HeadMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeCallbackController {

    private final OnlyOfficeIntegrationService onlyOfficeIntegrationService;

    @PostMapping("/callback")
    public ResponseEntity<OnlyOfficeCallbackResponseDto> callback(
            @RequestBody OnlyOfficeCallbackRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(onlyOfficeIntegrationService.handleCallback(request, authorizationHeader));
    }

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<ByteArrayResource> documentContent(
            @PathVariable("id") String id,
            @RequestParam("token") String token
    ) {
        DocumentMetadata document = onlyOfficeIntegrationService.validateContentToken(id, token);
        byte[] content = onlyOfficeIntegrationService.downloadContent(id, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                .body(new ByteArrayResource(content));
    }

    @HeadMapping("/documents/{id}/content")
    public ResponseEntity<Void> documentContentHead(
            @PathVariable("id") String id,
            @RequestParam("token") String token
    ) {
        DocumentMetadata document = onlyOfficeIntegrationService.validateContentToken(id, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .contentLength(document.getSize() == null ? 0 : document.getSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                .build();
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName.replace("\"", "");
    }
}
