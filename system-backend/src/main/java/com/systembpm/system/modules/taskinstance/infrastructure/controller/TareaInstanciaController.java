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
}
