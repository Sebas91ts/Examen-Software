package com.systembpm.system.modules.taskinstance.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.taskinstance.application.dto.TareaInstanciaResponseDto;
import com.systembpm.system.modules.taskinstance.application.service.ITareaInstanciaService;
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
@RequestMapping("/api/tarea-instancias")
@RequiredArgsConstructor
public class TareaInstanciaController {

    private final ITareaInstanciaService tareaInstanciaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listar() {
        log.info("Solicitud GET /api/tarea-instancias");
        return ResponseEntity.ok(ApiResponse.success("Tareas listadas exitosamente", tareaInstanciaService.listar()));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listarPendientes() {
        log.info("Solicitud GET /api/tarea-instancias/pendientes");
        return ResponseEntity.ok(
                ApiResponse.success("Tareas pendientes listadas exitosamente", tareaInstanciaService.listarPendientes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TareaInstanciaResponseDto>> obtenerPorId(@PathVariable String id) {
        log.info("Solicitud GET /api/tarea-instancias/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Tarea obtenida exitosamente", tareaInstanciaService.obtenerPorId(id)));
    }

    @GetMapping("/instancia/{processInstanceId}")
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listarPorInstancia(
            @PathVariable String processInstanceId) {
        log.info("Solicitud GET /api/tarea-instancias/instancia/{}", processInstanceId);
        return ResponseEntity.ok(
                ApiResponse.success("Tareas de la instancia listadas exitosamente",
                        tareaInstanciaService.listarPorInstancia(processInstanceId)));
    }

    @GetMapping("/area/{areaId}")
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listarPorArea(@PathVariable String areaId) {
        log.info("Solicitud GET /api/tarea-instancias/area/{}", areaId);
        return ResponseEntity.ok(
                ApiResponse.success("Tareas del area listadas exitosamente", tareaInstanciaService.listarPorArea(areaId)));
    }

    @GetMapping("/usuario/{assignedTo}")
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listarPorUsuario(
            @PathVariable String assignedTo) {
        log.info("Solicitud GET /api/tarea-instancias/usuario/{}", assignedTo);
        return ResponseEntity.ok(
                ApiResponse.success("Tareas del usuario listadas exitosamente",
                        tareaInstanciaService.listarPorUsuario(assignedTo)));
    }

    @GetMapping("/proceso/{nombreProceso}")
    public ResponseEntity<ApiResponse<List<TareaInstanciaResponseDto>>> listarPorProceso(
            @PathVariable String nombreProceso) {
        log.info("Solicitud GET /api/tarea-instancias/proceso/{}", nombreProceso);
        return ResponseEntity.ok(
                ApiResponse.success("Tareas del proceso listadas exitosamente",
                        tareaInstanciaService.listarPorProceso(nombreProceso)));
    }
}
