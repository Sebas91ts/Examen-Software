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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
        return buildDocumentContentResponse(id, token);
    }

    @GetMapping("/documents/{id}/content/{fileName}")
    public ResponseEntity<ByteArrayResource> documentContentWithFileName(
            @PathVariable("id") String id,
            @PathVariable("fileName") String fileName,
            @RequestParam("token") String token
    ) {
        return buildDocumentContentResponse(id, token);
    }

    @RequestMapping(value = "/documents/{id}/content", method = RequestMethod.HEAD)
    public ResponseEntity<Void> documentContentHead(
            @PathVariable("id") String id,
            @RequestParam("token") String token
    ) {
        return buildDocumentContentHeadResponse(id, token);
    }

    @RequestMapping(value = "/documents/{id}/content/{fileName}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> documentContentHeadWithFileName(
            @PathVariable("id") String id,
            @PathVariable("fileName") String fileName,
            @RequestParam("token") String token
    ) {
        return buildDocumentContentHeadResponse(id, token);
    }

    private ResponseEntity<ByteArrayResource> buildDocumentContentResponse(String id, String token) {
        DocumentMetadata document = onlyOfficeIntegrationService.validateContentToken(id, token);
        byte[] content = onlyOfficeIntegrationService.downloadContent(id, token);
        MediaType contentType = resolveContentType(document);
        log.info("onlyoffice.content.response documentId={} fileName={} mimeType={} resolvedContentType={} bytes={} magic={}",
                document.getId(), document.getOriginalName(), document.getMimeType(), contentType, content.length, magic(content));
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                .body(new ByteArrayResource(content));
    }

    private ResponseEntity<Void> buildDocumentContentHeadResponse(String id, String token) {
        DocumentMetadata document = onlyOfficeIntegrationService.validateContentToken(id, token);
        byte[] content = onlyOfficeIntegrationService.downloadContent(id, token);
        MediaType contentType = resolveContentType(document);
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                .build();
    }

    private MediaType resolveContentType(DocumentMetadata document) {
        String fileName = document.getOriginalName() == null ? "" : document.getOriginalName().toLowerCase();
        if (fileName.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (fileName.endsWith(".doc")) {
            return MediaType.parseMediaType("application/msword");
        }
        if (fileName.endsWith(".xlsx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (fileName.endsWith(".xls")) {
            return MediaType.parseMediaType("application/vnd.ms-excel");
        }
        if (fileName.endsWith(".pptx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
        if (fileName.endsWith(".ppt")) {
            return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        }
        if (fileName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (document.getMimeType() != null && !document.getMimeType().isBlank()) {
            return MediaType.parseMediaType(document.getMimeType());
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String magic(byte[] content) {
        if (content == null || content.length < 4) {
            return "n/a";
        }
        return String.format("%02X%02X%02X%02X", content[0], content[1], content[2], content[3]);
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        return fileName.replace("\"", "");
    }
}
