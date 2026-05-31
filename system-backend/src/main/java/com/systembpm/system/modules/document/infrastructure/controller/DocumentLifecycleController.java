package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.DocumentLifecycleActionRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentLifecycleController {

    private final DocumentLifecycleService documentLifecycleService;

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> approve(
            @PathVariable("id") String id,
            @RequestBody(required = false) DocumentLifecycleActionRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Documento aprobado",
                documentLifecycleService.approve(id, request != null ? request.getComment() : null, requester)
        ));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> reject(
            @PathVariable("id") String id,
            @RequestBody(required = false) DocumentLifecycleActionRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Documento rechazado",
                documentLifecycleService.reject(id, request != null ? request.getComment() : null, requester)
        ));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> lock(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Documento bloqueado", documentLifecycleService.lock(id, requester)));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> unlock(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Documento desbloqueado", documentLifecycleService.unlock(id, requester)));
    }

    @GetMapping("/task/{processInstanceId}/{taskDefinitionKey}")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponseDto>>> getByTask(
            @PathVariable String processInstanceId,
            @PathVariable String taskDefinitionKey,
            @RequestParam(required = false) String taskInstanceId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Documentos de tarea obtenidos",
                documentLifecycleService.getByTask(processInstanceId, taskDefinitionKey, taskInstanceId, requester)
        ));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponseDto>>> getPending(
            @RequestParam(required = false) String processInstanceId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Documentos pendientes obtenidos",
                documentLifecycleService.getPending(processInstanceId, requester)
        ));
    }
}
