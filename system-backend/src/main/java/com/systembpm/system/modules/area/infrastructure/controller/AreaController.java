package com.systembpm.system.modules.area.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.area.application.dto.AreaCreateDto;
import com.systembpm.system.modules.area.application.dto.AreaResponseDto;
import com.systembpm.system.modules.area.application.dto.AreaUpdateDto;
import com.systembpm.system.modules.area.application.service.IAreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final IAreaService areaService;

    @PostMapping
    public ResponseEntity<ApiResponse<AreaResponseDto>> crear(@Valid @RequestBody AreaCreateDto dto) {
        log.info("Solicitud POST /api/areas");
        AreaResponseDto area = areaService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Area creada exitosamente", area));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AreaResponseDto>>> listarTodas() {
        log.info("Solicitud GET /api/areas");
        return ResponseEntity.ok(ApiResponse.success("Areas listadas exitosamente", areaService.listarTodas()));
    }

    @GetMapping("/activas")
    public ResponseEntity<ApiResponse<List<AreaResponseDto>>> listarActivas() {
        log.info("Solicitud GET /api/areas/activas");
        return ResponseEntity.ok(ApiResponse.success("Areas activas listadas exitosamente", areaService.listarActivas()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AreaResponseDto>> obtenerPorId(@PathVariable String id) {
        log.info("Solicitud GET /api/areas/{}", id);
        return areaService.obtenerPorId(id)
                .map(area -> ResponseEntity.ok(ApiResponse.success("Area encontrada", area)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Area no encontrada")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AreaResponseDto>> actualizar(
            @PathVariable String id,
            @Valid @RequestBody AreaUpdateDto dto) {
        log.info("Solicitud PUT /api/areas/{}", id);
        return areaService.actualizar(id, dto)
                .map(area -> ResponseEntity.ok(ApiResponse.success("Area actualizada exitosamente", area)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Area no encontrada")));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable String id) {
        log.info("Solicitud PATCH /api/areas/{}/desactivar", id);
        boolean desactivada = areaService.desactivar(id);
        if (!desactivada) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Area no encontrada"));
        }

        return ResponseEntity.ok(ApiResponse.success("Area desactivada exitosamente", null));
    }
}
