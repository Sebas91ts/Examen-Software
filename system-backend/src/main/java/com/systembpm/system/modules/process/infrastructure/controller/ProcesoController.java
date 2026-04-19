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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
