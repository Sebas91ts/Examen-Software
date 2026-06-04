package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditingSessionResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditorConfigResponseDto;
import com.systembpm.system.modules.document.application.service.OnlyOfficeIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class OnlyOfficePreparationController {

    private final OnlyOfficeIntegrationService onlyOfficeIntegrationService;

    @GetMapping("/{id}/editor-config")
    public ResponseEntity<ApiResponse<OnlyOfficeEditorConfigResponseDto>> getEditorConfig(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Configuracion de editor obtenida", onlyOfficeIntegrationService.getEditorConfig(id, requester)));
    }

    @PostMapping("/{id}/start-editing")
    public ResponseEntity<ApiResponse<OnlyOfficeEditingSessionResponseDto>> startEditing(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Sesion de edicion iniciada", onlyOfficeIntegrationService.startEditing(id, requester)));
    }

    @PostMapping("/{id}/finish-editing")
    public ResponseEntity<ApiResponse<OnlyOfficeEditingSessionResponseDto>> finishEditing(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Sesion de edicion finalizada", onlyOfficeIntegrationService.finishEditing(id, requester)));
    }
}
