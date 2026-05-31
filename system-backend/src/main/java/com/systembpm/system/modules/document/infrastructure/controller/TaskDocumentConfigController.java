package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigCreateDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;
import com.systembpm.system.modules.document.application.service.TaskDocumentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents/task-config")
@RequiredArgsConstructor
public class TaskDocumentConfigController {

    private final TaskDocumentConfigService taskDocumentConfigService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskDocumentConfigResponseDto>> save(
            @Valid @RequestBody TaskDocumentConfigCreateDto dto,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        log.info("Solicitud POST /api/documents/task-config processKey={} version={} taskDefinitionKey={}",
                dto.getProcessKey(), dto.getProcessVersion(), dto.getTaskDefinitionKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Configuracion documental guardada", taskDocumentConfigService.save(dto, requester)));
    }

    @GetMapping("/{processKey}/{version}/{taskDefinitionKey}")
    public ResponseEntity<ApiResponse<TaskDocumentConfigResponseDto>> get(
            @PathVariable String processKey,
            @PathVariable Integer version,
            @PathVariable String taskDefinitionKey,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        log.info("Solicitud GET /api/documents/task-config/{}/{}/{}", processKey, version, taskDefinitionKey);
        return taskDocumentConfigService.get(processKey, version, taskDefinitionKey, requester)
                .map(config -> ResponseEntity.ok(ApiResponse.success("Configuracion documental encontrada", config)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Configuracion documental no encontrada")));
    }

    @PostMapping("/validate-upload")
    public ResponseEntity<ApiResponse<TaskDocumentUploadValidationResponseDto>> validateUpload(
            @Valid @RequestBody TaskDocumentUploadValidationRequestDto dto,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        TaskDocumentUploadValidationResponseDto result = taskDocumentConfigService.validateUpload(dto, requester);
        return ResponseEntity.ok(ApiResponse.success("Validacion completada", result));
    }
}

