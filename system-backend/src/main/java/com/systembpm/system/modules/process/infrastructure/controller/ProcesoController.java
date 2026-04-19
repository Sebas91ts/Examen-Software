package com.systembpm.system.modules.process.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.application.service.IProcesoService;
import com.systembpm.system.modules.process.domain.Proceso;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para persistir procesos BPMN.
 */
@Slf4j
@RestController
@RequestMapping("/api/procesos")
@RequiredArgsConstructor
public class ProcesoController {

    private final IProcesoService procesoService;

    /**
     * Guarda un proceso BPMN en MongoDB.
     *
     * POST /api/procesos
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Proceso>> guardarProceso(
            @Valid @RequestBody ProcesoCreateDto dto) {
        log.info("Solicitud POST /api/procesos para proceso: {}", dto.getNombre());

        Proceso procesoGuardado = procesoService.guardar(dto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Proceso BPMN guardado exitosamente", procesoGuardado));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Proceso>>> listarProcesos() {
        log.info("Solicitud GET /api/procesos");

        List<Proceso> procesos = procesoService.listar();

        return ResponseEntity.ok(ApiResponse.success("Procesos obtenidos exitosamente", procesos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Proceso>> obtenerProceso(@PathVariable String id) {
        log.info("Solicitud GET /api/procesos/{}", id);

        Proceso proceso = procesoService.obtenerPorId(id);

        return ResponseEntity.ok(ApiResponse.success("Proceso obtenido exitosamente", proceso));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Proceso>> actualizarProceso(
            @PathVariable String id,
            @Valid @RequestBody ProcesoCreateDto dto) {
        log.info("Solicitud PUT /api/procesos/{} para proceso: {}", id, dto.getNombre());

        Proceso procesoActualizado = procesoService.actualizar(id, dto);

        return ResponseEntity.ok(ApiResponse.success("Proceso BPMN actualizado exitosamente", procesoActualizado));
    }

    @PutMapping("/{id}/publicar")
    public ResponseEntity<ApiResponse<Proceso>> publicarProceso(@PathVariable String id) {
        Proceso procesoPublicado = procesoService.publicar(id);
        return ResponseEntity.ok(ApiResponse.success("Proceso publicado exitosamente", procesoPublicado));
    }
}
