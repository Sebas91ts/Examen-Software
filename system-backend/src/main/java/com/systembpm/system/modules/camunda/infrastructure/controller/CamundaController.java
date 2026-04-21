package com.systembpm.system.modules.camunda.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.camunda.application.dto.StartProcessRequestDto;
import com.systembpm.system.modules.camunda.application.service.CamundaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/camunda")
@RequiredArgsConstructor
public class CamundaController {

    private final CamundaService camundaService;

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
                ApiResponse.success("Tareas activas listadas exitosamente", camundaService.listarTareas()));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> task(@PathVariable String taskId) {
        log.info("Solicitud GET /api/camunda/tasks/{}", taskId);
        return ResponseEntity.ok(
                ApiResponse.success("Detalle de tarea obtenido exitosamente", camundaService.obtenerTarea(taskId)));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> complete(@PathVariable String taskId) {
        log.info("Solicitud POST /api/camunda/tasks/{}/complete", taskId);
        return ResponseEntity.ok(
                ApiResponse.success("Tarea completada exitosamente", camundaService.completarTarea(taskId)));
    }
}
