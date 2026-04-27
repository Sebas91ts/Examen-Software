package com.systembpm.system.modules.client.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.client.application.dto.ClientProcessInstanceListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessListItemDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

        ClientProcessStartResponseDto response = clientProcessService.iniciarTramite(
                processId,
                authentication.getName(),
                request != null ? request.getVariables() : null);
        return ResponseEntity.ok(ApiResponse.success("Tramite iniciado correctamente", response));
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
}
