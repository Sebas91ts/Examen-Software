package com.systembpm.system.modules.camunda.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.audit.application.dto.AuditRecordRequest;
import com.systembpm.system.modules.audit.application.service.AuditService;
import com.systembpm.system.modules.audit.domain.AuditAction;
import com.systembpm.system.modules.audit.domain.AuditEntityType;
import com.systembpm.system.modules.document.application.dto.TaskDocumentRuntimeResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentTaskRuntimeService;
import com.systembpm.system.modules.camunda.application.service.CamundaServiceImpl;
import com.systembpm.system.modules.notification.application.service.NotificationServiceImpl;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.security.application.service.AuthService;
import com.systembpm.system.modules.taskexecutionlog.application.service.ITaskExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/camunda")
@RequiredArgsConstructor
public class CamundaController {

    private final CamundaServiceImpl camundaService;
    private final AuthService authService;
    private final ITaskExecutionLogService taskExecutionLogService;
    private final NotificationServiceImpl notificationService;
    private final IRealtimeEventService realtimeEventService;
    private final DocumentTaskRuntimeService documentTaskRuntimeService;
    private final AuditService auditService;

    @PostMapping("/deploy/{procesoId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deploy(@PathVariable String procesoId) {
        log.info("Solicitud POST /api/camunda/deploy/{}", procesoId);
        return ResponseEntity.ok(
                ApiResponse.success("Proceso desplegado en Camunda exitosamente", camundaService.desplegarProceso(procesoId)));
    }

    @PostMapping("/start/{processKey}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(@PathVariable String processKey) {
        log.info("Solicitud POST /api/camunda/start/{}", processKey);
        Map<String, Object> response = camundaService.iniciarInstancia(processKey);
        publicarEventosTareasIniciales(response);
        return ResponseEntity.ok(
                ApiResponse.success("Instancia iniciada en Camunda exitosamente", response));
    }

    @PostMapping("/start/{processKey}/business/{businessKey}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startWithBusinessKey(
            @PathVariable String processKey,
            @PathVariable String businessKey) {
        log.info("Solicitud POST /api/camunda/start/{}/business/{}", processKey, businessKey);
        Map<String, Object> response = camundaService.iniciarInstanciaPorDefinicion(processKey, businessKey);
        publicarEventosTareasIniciales(response);
        return ResponseEntity.ok(
                ApiResponse.success("Instancia iniciada en Camunda exitosamente",
                        response));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tasks() {
        log.info("Solicitud GET /api/camunda/tasks");
        return ResponseEntity.ok(
                ApiResponse.success("Tareas activas listadas exitosamente", camundaService.listarTareasTodas()));
    }

    @GetMapping("/tasks/my")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myTasks() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/camunda/tasks/my para {}", authentication.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Mis tareas listadas exitosamente",
                        camundaService.listarTareasPorAssignee(authentication.getName())));
    }

    @GetMapping("/tasks/my-area")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myAreaTasks() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        String userEmail = authentication.getName();
        var usuario = authService.obtenerUsuarioAutenticado(userEmail);
        if (usuario.getAreaId() == null || usuario.getAreaId().isBlank()) {
            return ResponseEntity.ok(ApiResponse.success("Tareas de tu area listadas exitosamente", List.of()));
        }

        log.info("Solicitud GET /api/camunda/tasks/my-area para {}", userEmail);
        return ResponseEntity.ok(
                ApiResponse.success("Tareas de tu area listadas exitosamente",
                        camundaService.listarTareasPorArea(usuario.getAreaId())));
    }

    @GetMapping("/tasks/all")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> allTasks() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        if (authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes permisos para ver todas las tareas"));
        }

        log.info("Solicitud GET /api/camunda/tasks/all por {}", authentication.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Todas las tareas listadas exitosamente", camundaService.listarTareasTodas()));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> task(@PathVariable String taskId) {
        log.info("Solicitud GET /api/camunda/tasks/{}", taskId);
        return ResponseEntity.ok(
                ApiResponse.success("Detalle de tarea obtenido exitosamente", camundaService.obtenerTarea(taskId)));
    }

    @GetMapping("/tasks/{taskId}/documents")
    public ResponseEntity<ApiResponse<TaskDocumentRuntimeResponseDto>> taskDocuments(@PathVariable String taskId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/camunda/tasks/{}/documents para {}", taskId, authentication.getName());
        Map<String, Object> taskSnapshot = camundaService.obtenerTarea(taskId);
        return ResponseEntity.ok(ApiResponse.success(
                "Runtime documental de tarea obtenido",
                documentTaskRuntimeService.getRuntime(taskSnapshot, authentication.getName())
        ));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> complete(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> body) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud POST /api/camunda/tasks/{}/complete", taskId);
        Map<String, Object> variables = Map.of();
        if (body != null) {
            Object rawVariables = body.get("variables");
            if (rawVariables instanceof Map<?, ?> mapVariables) {
                variables = mapVariables.entrySet().stream()
                        .filter(entry -> entry.getKey() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue,
                                (left, right) -> right,
                                java.util.LinkedHashMap::new));
            }
        }

        Map<String, Object> taskSnapshot = camundaService.obtenerTarea(taskId);
        Map<String, Object> response = camundaService.completarTarea(taskId, variables, authentication.getName());
        try {
            taskExecutionLogService.registrarEjecucion(taskSnapshot, variables, authentication.getName());
        } catch (Exception ex) {
            log.warn("No se pudo registrar el historial de ejecucion para la tarea {}. Se continua con notificaciones.", taskId, ex);
        }
        recordTaskCompleted(taskSnapshot, variables, authentication.getName());
        recordFormSubmitted(taskSnapshot, variables, authentication.getName());
        notificationService.notifyTaskCompleted(taskSnapshot, authentication.getName());
        realtimeEventService.publishTaskCompleted(taskSnapshot, authentication.getName());

        return ResponseEntity.ok(
                ApiResponse.success("Tarea completada exitosamente", response));
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(@PathVariable String taskId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesión autenticada"));
        }

        log.info("Solicitud POST /api/camunda/tasks/{}/claim por usuario {}", taskId, authentication.getName());
        Map<String, Object> taskSnapshot = camundaService.obtenerTarea(taskId);
        Map<String, Object> response = camundaService.tomarTarea(taskId, authentication.getName());
        recordTaskClaimed(taskSnapshot, authentication.getName());
        notificationService.notifyTaskClaimed(taskSnapshot, authentication.getName());
        realtimeEventService.publishTaskClaimed(taskSnapshot, authentication.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Tarea tomada exitosamente", response));
    }

    private void recordTaskCompleted(Map<String, Object> taskSnapshot, Map<String, Object> variables, String actorEmail) {
        auditService.record(AuditRecordRequest.builder()
                .action(AuditAction.TASK_COMPLETED)
                .entityType(AuditEntityType.TASK)
                .entityId(stringValue(taskSnapshot.get("id")))
                .entityName(resolveTaskName(taskSnapshot))
                .actorEmail(actorEmail)
                .tenantId(stringValue(taskSnapshot.get("areaId")))
                .areaId(stringValue(taskSnapshot.get("areaId")))
                .areaName(stringValue(taskSnapshot.get("areaNombre")))
                .processKey(resolveProcessKey(taskSnapshot))
                .processVersion(resolveProcessVersion(taskSnapshot))
                .processInstanceId(stringValue(taskSnapshot.get("processInstanceId")))
                .taskDefinitionKey(stringValue(taskSnapshot.get("taskDefinitionKey")))
                .taskInstanceId(stringValue(taskSnapshot.get("id")))
                .taskName(resolveTaskName(taskSnapshot))
                .afterSnapshot(Map.of(
                        "completed", true,
                        "variables", variables == null ? Map.of() : variables
                ))
                .metadata(Map.of("variablesCount", variables == null ? 0 : variables.size()))
                .build());
    }

    private void recordFormSubmitted(Map<String, Object> taskSnapshot, Map<String, Object> variables, String actorEmail) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        auditService.record(AuditRecordRequest.builder()
                .action(AuditAction.FORM_SUBMITTED)
                .entityType(AuditEntityType.FORM)
                .entityId(stringValue(taskSnapshot.get("id")))
                .entityName(resolveTaskName(taskSnapshot))
                .actorEmail(actorEmail)
                .tenantId(stringValue(taskSnapshot.get("areaId")))
                .areaId(stringValue(taskSnapshot.get("areaId")))
                .areaName(stringValue(taskSnapshot.get("areaNombre")))
                .processKey(resolveProcessKey(taskSnapshot))
                .processVersion(resolveProcessVersion(taskSnapshot))
                .processInstanceId(stringValue(taskSnapshot.get("processInstanceId")))
                .taskDefinitionKey(stringValue(taskSnapshot.get("taskDefinitionKey")))
                .taskInstanceId(stringValue(taskSnapshot.get("id")))
                .taskName(resolveTaskName(taskSnapshot))
                .afterSnapshot(Map.of("formData", variables))
                .metadata(Map.of("fields", variables.keySet()))
                .build());
    }

    private void recordTaskClaimed(Map<String, Object> taskSnapshot, String actorEmail) {
        auditService.record(AuditRecordRequest.builder()
                .action(AuditAction.TASK_CLAIMED)
                .entityType(AuditEntityType.TASK)
                .entityId(stringValue(taskSnapshot.get("id")))
                .entityName(resolveTaskName(taskSnapshot))
                .actorEmail(actorEmail)
                .tenantId(stringValue(taskSnapshot.get("areaId")))
                .areaId(stringValue(taskSnapshot.get("areaId")))
                .areaName(stringValue(taskSnapshot.get("areaNombre")))
                .processKey(resolveProcessKey(taskSnapshot))
                .processVersion(resolveProcessVersion(taskSnapshot))
                .processInstanceId(stringValue(taskSnapshot.get("processInstanceId")))
                .taskDefinitionKey(stringValue(taskSnapshot.get("taskDefinitionKey")))
                .taskInstanceId(stringValue(taskSnapshot.get("id")))
                .taskName(resolveTaskName(taskSnapshot))
                .afterSnapshot(Map.of("assignee", actorEmail))
                .build());
    }

    private void publicarEventosTareasIniciales(Map<String, Object> startResponse) {
        String processInstanceId = extractProcessInstanceId(startResponse);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            log.warn("No se pudo publicar eventos iniciales porque la respuesta de inicio no contiene processInstanceId");
            return;
        }

        List<Map<String, Object>> tareasIniciales = camundaService.listarTareasTodas().stream()
                .filter(tarea -> processInstanceId.equals(String.valueOf(tarea.get("processInstanceId"))))
                .toList();

        log.info("Se encontraron {} tareas iniciales para processInstanceId={}", tareasIniciales.size(), processInstanceId);

        for (Map<String, Object> tarea : tareasIniciales) {
            String areaId = stringValue(tarea.get("areaId"));
            String areaNombre = stringValue(tarea.get("areaNombre"));
            String taskId = stringValue(tarea.get("id"));
            String taskName = resolveTaskName(tarea);

            notificationService.notifyTaskAvailableForArea(areaId, areaNombre, processInstanceId, taskId, taskName);
            realtimeEventService.publishTaskCreated(tarea);
        }
    }

    private String extractProcessInstanceId(Map<String, Object> startResponse) {
        if (startResponse == null || startResponse.isEmpty()) {
            return null;
        }

        String processInstanceId = stringValue(startResponse.get("processInstanceId"));
        if (processInstanceId != null && !processInstanceId.isBlank()) {
            return processInstanceId.trim();
        }

        processInstanceId = stringValue(startResponse.get("id"));
        return processInstanceId != null ? processInstanceId.trim() : null;
    }

    private String resolveTaskName(Map<String, Object> tarea) {
        String name = stringValue(tarea.get("name"));
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

        name = stringValue(tarea.get("nombreTarea"));
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

        name = stringValue(tarea.get("taskDefinitionKey"));
        return name != null && !name.isBlank() ? name.trim() : "Tarea sin nombre";
    }

    private String resolveProcessKey(Map<String, Object> taskSnapshot) {
        String processKey = stringValue(taskSnapshot.get("processKey"));
        if (processKey != null && !processKey.isBlank()) {
            return processKey.trim();
        }
        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        int separatorIndex = processDefinitionId.indexOf(':');
        return separatorIndex <= 0 ? processDefinitionId.trim() : processDefinitionId.substring(0, separatorIndex).trim();
    }

    private Integer resolveProcessVersion(Map<String, Object> taskSnapshot) {
        Object rawVersion = taskSnapshot.get("processVersion");
        if (rawVersion instanceof Number number) {
            return number.intValue();
        }
        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        String[] parts = processDefinitionId.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.valueOf(parts[1].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
