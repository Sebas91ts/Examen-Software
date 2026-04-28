package com.systembpm.system.modules.client.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.client.application.dto.ClientProcessInstanceListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessTrackingResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartPreviewDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartRequestDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartResponseDto;
import com.systembpm.system.modules.client.application.service.ClientProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/client")
public class ClientProcessController {
    private final ClientProcessService clientProcessService;

    @GetMapping("/processes")
    public ResponseEntity<ApiResponse<List<ClientProcessListItemDto>>> listarProcesosDisponibles() {
        log.info("Solicitud GET /api/client/processes");

        List<ClientProcessListItemDto> procesos = clientProcessService.listarProcesosDisponibles();

        return ResponseEntity.ok(ApiResponse.success("Procesos cliente obtenidos exitosamente", procesos));
    }

    @GetMapping("/processes/{processId}/start-preview")
    public ResponseEntity<ApiResponse<ClientProcessStartPreviewDto>> obtenerVistaInicio(@PathVariable String processId) {
        log.info("Solicitud GET /api/client/processes/{}/start-preview", processId);
        ClientProcessStartPreviewDto preview = clientProcessService.obtenerVistaInicio(processId);
        return ResponseEntity.ok(ApiResponse.success("Vista previa de inicio obtenida exitosamente", preview));
    }

    @PostMapping("/processes/{processId}/start")
    public ResponseEntity<ApiResponse<ClientProcessStartResponseDto>> iniciarTramite(
            @PathVariable String processId,
            @RequestBody(required = false) ClientProcessStartRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud POST /api/client/processes/{}/start por {}", processId, authentication.getName());
        try {
            ClientProcessStartResponseDto response = clientProcessService.iniciarTramite(
                    processId,
                    authentication.getName(),
                    request != null ? request.getVariables() : null);
            return ResponseEntity.ok(ApiResponse.success("Tramite iniciado correctamente", response));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudo iniciar el tramite";
            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        } catch (Throwable ex) {
            log.error("Error inesperado iniciando tramite de cliente {}", processId, ex);
            return ResponseEntity.status(500).body(ApiResponse.error("No se pudo iniciar el tramite del cliente"));
        }
    }

    @GetMapping("/instances")
    public ResponseEntity<ApiResponse<List<ClientProcessInstanceListItemDto>>> listarMisInstancias() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/client/instances para {}", authentication.getName());
        List<ClientProcessInstanceListItemDto> instancias = clientProcessService.listarMisInstancias(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Instancias del cliente obtenidas exitosamente", instancias));
    }

    @GetMapping("/instances/{processInstanceId}/tracking")
    public ResponseEntity<ApiResponse<ClientProcessTrackingResponseDto>> obtenerTrackingCliente(
            @PathVariable String processInstanceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No hay una sesion autenticada"));
        }

        log.info("Solicitud GET /api/client/instances/{}/tracking para {}", processInstanceId, authentication.getName());
        try {
            ClientProcessTrackingResponseDto tracking = clientProcessService.obtenerTrackingCliente(processInstanceId, authentication.getName());
            return ResponseEntity.ok(ApiResponse.success("Tracking del cliente obtenido exitosamente", tracking));
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "No se pudo obtener el seguimiento";
            if (message.toLowerCase().contains("acceso") || message.toLowerCase().contains("cliente")) {
                return ResponseEntity.status(403).body(ApiResponse.error(message));
            }

            return ResponseEntity.badRequest().body(ApiResponse.error(message));
        } catch (Throwable ex) {
            log.error("Error inesperado obteniendo tracking de cliente {}", processInstanceId, ex);
            return ResponseEntity.status(500).body(ApiResponse.error("No se pudo obtener el seguimiento del cliente"));
        }
    }
}
