package com.systembpm.system.modules.processtracking.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.processtracking.application.dto.ProcessInstanceTrackingResponseDto;
import com.systembpm.system.modules.processtracking.application.service.IProcessTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/process-instances")
@RequiredArgsConstructor
public class ProcessTrackingController {

    private final IProcessTrackingService processTrackingService;

    @GetMapping("/{processInstanceId}/tracking")
    public ResponseEntity<ApiResponse<ProcessInstanceTrackingResponseDto>> obtenerTracking(
            @PathVariable String processInstanceId) {
        log.info("Solicitud GET /api/process-instances/{}/tracking", processInstanceId);
        return ResponseEntity.ok(ApiResponse.success(
                "Tracking de la instancia obtenido exitosamente",
                processTrackingService.obtenerTracking(processInstanceId)));
    }
}
