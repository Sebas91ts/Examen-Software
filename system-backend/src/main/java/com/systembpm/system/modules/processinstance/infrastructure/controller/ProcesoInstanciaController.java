package com.systembpm.system.modules.processinstance.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.processinstance.application.dto.ProcesoInstanciaResponseDto;
import com.systembpm.system.modules.processinstance.application.service.IProcesoInstanciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/proceso-instancias")
@RequiredArgsConstructor
public class ProcesoInstanciaController {

    private final IProcesoInstanciaService procesoInstanciaService;

    @PostMapping("/{processDefinitionId}/iniciar")
    public ResponseEntity<ApiResponse<ProcesoInstanciaResponseDto>> iniciarDesdeDefinicion(
            @PathVariable String processDefinitionId) {
        log.info("Solicitud POST /api/proceso-instancias/{}/iniciar", processDefinitionId);

        ProcesoInstanciaResponseDto instancia = procesoInstanciaService.iniciarDesdeDefinicion(processDefinitionId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Instancia de proceso iniciada exitosamente", instancia));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProcesoInstanciaResponseDto>>> listar() {
        log.info("Solicitud GET /api/proceso-instancias");

        List<ProcesoInstanciaResponseDto> instancias = procesoInstanciaService.listar();

        return ResponseEntity.ok(ApiResponse.success("Instancias listadas exitosamente", instancias));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcesoInstanciaResponseDto>> obtenerPorId(@PathVariable String id) {
        log.info("Solicitud GET /api/proceso-instancias/{}", id);

        ProcesoInstanciaResponseDto instancia = procesoInstanciaService.obtenerPorId(id);

        return ResponseEntity.ok(ApiResponse.success("Instancia obtenida exitosamente", instancia));
    }
}
