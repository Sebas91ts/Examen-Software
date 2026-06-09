package com.systembpm.system.modules.audit.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.audit.application.dto.AuditEventResponseDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchRequestDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchResponseDto;
import com.systembpm.system.modules.audit.domain.AuditAction;
import com.systembpm.system.modules.audit.domain.AuditEntityType;
import com.systembpm.system.modules.audit.application.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<AuditEventSearchResponseDto>> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String taskInstanceId,
            @RequestParam(required = false) String taskDefinitionKey,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String createdAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String createdAtTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        log.info("Solicitud GET /api/audit/events por {}", requester);
        AuditEventSearchRequestDto request = AuditEventSearchRequestDto.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actorEmail(actorEmail)
                .areaId(areaId)
                .processInstanceId(processInstanceId)
                .processKey(processKey)
                .taskInstanceId(taskInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .documentId(documentId)
                .createdAtFrom(createdAtFrom)
                .createdAtTo(createdAtTo)
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Eventos de auditoria obtenidos", auditService.search(request, requester)));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<List<AuditEventResponseDto>>> byDocument(
            @PathVariable String documentId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Historial documental obtenido", auditService.listByDocument(documentId, requester)));
    }

    @GetMapping("/process-instances/{processInstanceId}")
    public ResponseEntity<ApiResponse<List<AuditEventResponseDto>>> byProcessInstance(
            @PathVariable String processInstanceId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Historial de instancia obtenido", auditService.listByProcessInstance(processInstanceId, requester)));
    }

    @GetMapping("/tasks/{taskInstanceId}")
    public ResponseEntity<ApiResponse<List<AuditEventResponseDto>>> byTask(
            @PathVariable String taskInstanceId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Historial de tarea obtenido", auditService.listByTask(taskInstanceId, requester)));
    }
}
