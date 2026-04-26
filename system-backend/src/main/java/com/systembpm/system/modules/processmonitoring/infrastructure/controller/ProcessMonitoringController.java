package com.systembpm.system.modules.processmonitoring.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.processmonitoring.application.dto.ProcessMonitoringResponseDto;
import com.systembpm.system.modules.processmonitoring.application.service.IProcessMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/procesos")
@RequiredArgsConstructor
public class ProcessMonitoringController {

    private final IProcessMonitoringService processMonitoringService;

    @GetMapping("/{id}/monitoring")
    public ResponseEntity<ApiResponse<ProcessMonitoringResponseDto>> obtenerMonitoreo(@PathVariable String id) {
        log.info("Solicitud GET /api/procesos/{}/monitoring", id);
        return ResponseEntity.ok(ApiResponse.success(
                "Monitoreo del proceso obtenido exitosamente",
                processMonitoringService.obtenerMonitoreo(id)));
    }
}
