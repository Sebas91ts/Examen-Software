package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentTagController {

    private final DocumentTagService documentTagService;

    @PostMapping("/{documentId}/tags/{tagId}")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> addTag(
            @PathVariable String documentId,
            @PathVariable String tagId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tag asociado al documento", documentTagService.addTag(documentId, tagId, requester)));
    }

    @DeleteMapping("/{documentId}/tags/{tagId}")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> removeTag(
            @PathVariable String documentId,
            @PathVariable String tagId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tag removido del documento", documentTagService.removeTag(documentId, tagId, requester)));
    }
}
