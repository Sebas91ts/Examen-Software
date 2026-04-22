package com.systembpm.system.modules.camunda.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.camunda.application.dto.StartProcessRequestDto;
import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.security.application.service.AuthService;
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

    private final CamundaService camundaService;
    private final AuthService authService;

    @PostMapping("/deploy/{procesoId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deploy(@PathVariable String procesoId) {
        log.info("Solicitud POST /api/camunda/deploy/{}", procesoId);
        return ResponseEntity.ok(
                ApiResponse.success("Proceso desplegado en Camunda exitosamente", camundaService.desplegarProceso(procesoId)));
    }

    @PostMapping("/start/{processKey}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(@PathVariable String processKey) {
        log.info("Solicitud POST /api/camunda/start/{}", processKey);
        return ResponseEntity.ok(
                ApiResponse.success("Instancia iniciada en Camunda exitosamente", camundaService.iniciarInstancia(processKey)));
    }

    @PostMapping("/start/{processKey}/business/{businessKey}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startWithBusinessKey(
            @PathVariable String processKey,
            @PathVariable String businessKey) {
        log.info("Solicitud POST /api/camunda/start/{}/business/{}", processKey, businessKey);
        return ResponseEntity.ok(
                ApiResponse.success("Instancia iniciada en Camunda exitosamente",
                        camundaService.iniciarInstanciaPorDefinicion(processKey, businessKey)));
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

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> complete(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> body) {
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
        return ResponseEntity.ok(
                ApiResponse.success("Tarea completada exitosamente",
                        camundaService.completarTarea(taskId, variables)));
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(@PathVariable String taskId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesión autenticada"));
        }

        log.info("Solicitud POST /api/camunda/tasks/{}/claim por usuario {}", taskId, authentication.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Tarea tomada exitosamente",
                        camundaService.tomarTarea(taskId, authentication.getName())));
    }
}
