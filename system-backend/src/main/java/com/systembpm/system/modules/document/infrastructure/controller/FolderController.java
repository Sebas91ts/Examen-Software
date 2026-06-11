package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.FolderResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderTreeResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderUpdateRequestDto;
import com.systembpm.system.modules.document.application.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponseDto>> create(
            @Valid @RequestBody FolderCreateRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Carpeta creada", folderService.create(request, requester)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponseDto>>> list(Authentication authentication) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Carpetas obtenidas", folderService.list(requester)));
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<FolderTreeResponseDto>>> tree(Authentication authentication) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Arbol de carpetas obtenido", folderService.tree(requester)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FolderResponseDto>> getById(
            @PathVariable String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Carpeta obtenida", folderService.getById(id, requester)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FolderResponseDto>> update(
            @PathVariable String id,
            @Valid @RequestBody FolderUpdateRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Carpeta actualizada", folderService.update(id, request, requester)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        folderService.delete(id, requester);
        return ResponseEntity.ok(ApiResponse.success("Carpeta eliminada", null));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponseDto>>> getDocuments(
            @PathVariable String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Documentos de carpeta obtenidos", folderService.getDocuments(id, requester)));
    }
}
