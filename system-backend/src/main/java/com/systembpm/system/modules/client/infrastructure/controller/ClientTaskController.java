package com.systembpm.system.modules.client.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.client.application.dto.ClientTaskCompleteResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskFormResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskListItemDto;
import com.systembpm.system.modules.client.application.service.ClientTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/client/tasks")
public class ClientTaskController {

    private static final JsonParser JSON_PARSER = JsonParserFactory.getJsonParser();
    private final ClientTaskService clientTaskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientTaskListItemDto>>> listarTareas() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/client/tasks para {}", authentication.getName());
        try {
            List<ClientTaskListItemDto> tareas = clientTaskService.listarTareasCliente(authentication.getName());
            return ResponseEntity.ok(ApiResponse.success("Tareas del cliente obtenidas exitosamente", tareas));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudieron listar las tareas";
            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        } catch (Throwable ex) {
            log.error("Error inesperado listando tareas de cliente {}", authentication.getName(), ex);
            return ResponseEntity.status(500).body(ApiResponse.error("No se pudieron listar las tareas del cliente"));
        }
    }

    @GetMapping("/{taskId}/form")
    public ResponseEntity<ApiResponse<ClientTaskFormResponseDto>> obtenerFormulario(@PathVariable String taskId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/client/tasks/{}/form para {}", taskId, authentication.getName());
        try {
            ClientTaskFormResponseDto response = clientTaskService.obtenerFormularioTarea(taskId, authentication.getName());
            return ResponseEntity.ok(ApiResponse.success("Formulario de tarea obtenido exitosamente", response));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudo obtener el formulario";
            if (message.toLowerCase().contains("acceso") || message.toLowerCase().contains("cliente")) {
                return ResponseEntity.status(403).body(ApiResponse.error(message));
            }

            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        } catch (Throwable ex) {
            log.error("Error inesperado obteniendo formulario de tarea {}", taskId, ex);
            return ResponseEntity.status(500).body(ApiResponse.error("No se pudo obtener el formulario de la tarea"));
        }
    }

    @PostMapping(value = "/{taskId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ClientTaskCompleteResponseDto>> completarTarea(
            @PathVariable String taskId,
            @RequestParam(value = "formData", required = false) String formData,
            @RequestParam(required = false) MultiValueMap<String, MultipartFile> files) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud POST /api/client/tasks/{}/complete para {}", taskId, authentication.getName());
        try {
            ClientTaskCompleteResponseDto response = clientTaskService.completarTarea(
                    taskId,
                    authentication.getName(),
                    parseFormData(formData),
                    files != null ? files : new org.springframework.util.LinkedMultiValueMap<>());
            return ResponseEntity.ok(ApiResponse.success("Tarea completada exitosamente", response));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudo completar la tarea";
            if (message.toLowerCase().contains("acceso") || message.toLowerCase().contains("cliente")) {
                return ResponseEntity.status(403).body(ApiResponse.error(message));
            }

            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        } catch (Throwable ex) {
            log.error("Error inesperado completando tarea de cliente {}", taskId, ex);
            return ResponseEntity.status(500).body(ApiResponse.error("No se pudo completar la tarea del cliente"));
        }
    }

    private Map<String, Object> parseFormData(String formData) {
        if (formData == null || formData.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> parsed = JSON_PARSER.parseMap(formData);
            return parsed != null ? parsed : Map.of();
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo analizar el formulario enviado", ex);
        }
    }
}
