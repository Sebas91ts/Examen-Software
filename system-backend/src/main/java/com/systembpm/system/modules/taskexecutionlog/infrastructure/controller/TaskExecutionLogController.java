package com.systembpm.system.modules.taskexecutionlog.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto;
import com.systembpm.system.modules.taskexecutionlog.application.service.ITaskExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/process-instances")
@RequiredArgsConstructor
public class TaskExecutionLogController {

    private final ITaskExecutionLogService taskExecutionLogService;

    @GetMapping("/{processInstanceId}/history")
    public ResponseEntity<ApiResponse<List<TaskExecutionLogResponseDto>>> listarHistorial(
            @PathVariable String processInstanceId) {
        log.info("Solicitud GET /api/process-instances/{}/history", processInstanceId);
        return ResponseEntity.ok(ApiResponse.success(
                "Historial de la instancia obtenido exitosamente",
                taskExecutionLogService.listarPorInstancia(processInstanceId)));
    }
}
