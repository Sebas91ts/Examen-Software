package com.systembpm.system.modules.dashboard.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.dashboard.application.dto.DashboardSummaryResponseDto;
import com.systembpm.system.modules.dashboard.application.service.IDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IDashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponseDto>> obtenerResumen() {
        log.info("Solicitud GET /api/dashboard/summary");
        return ResponseEntity.ok(ApiResponse.success(
                "Resumen del dashboard obtenido exitosamente",
                dashboardService.obtenerResumen()));
    }
}
