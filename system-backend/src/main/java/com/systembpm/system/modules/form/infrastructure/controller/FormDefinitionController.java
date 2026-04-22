package com.systembpm.system.modules.form.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.form.application.dto.FormDefinitionCreateDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionUpdateDto;
import com.systembpm.system.modules.form.application.service.IFormDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormDefinitionController {

    private final IFormDefinitionService formDefinitionService;

    @PostMapping
    public ResponseEntity<ApiResponse<FormDefinitionResponseDto>> crear(@Valid @RequestBody FormDefinitionCreateDto dto) {
        log.info("Solicitud POST /api/forms para processKey={}, taskDefinitionKey={}", dto.getProcessKey(), dto.getTaskDefinitionKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Formulario creado exitosamente", formDefinitionService.crear(dto)));
    }

    @GetMapping("/{processKey}/{version}/{taskDefinitionKey}")
    public ResponseEntity<ApiResponse<FormDefinitionResponseDto>> obtener(
            @PathVariable String processKey,
            @PathVariable Integer version,
            @PathVariable String taskDefinitionKey) {
        log.info("Solicitud GET /api/forms/{}/{}/{}", processKey, version, taskDefinitionKey);
        return formDefinitionService.obtenerPorClave(processKey, version, taskDefinitionKey)
                .map(form -> ResponseEntity.ok(ApiResponse.success("Formulario encontrado", form)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Formulario no encontrado")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FormDefinitionResponseDto>> actualizar(
            @PathVariable String id,
            @Valid @RequestBody FormDefinitionUpdateDto dto) {
        log.info("Solicitud PUT /api/forms/{}", id);
        return formDefinitionService.actualizar(id, dto)
                .map(form -> ResponseEntity.ok(ApiResponse.success("Formulario actualizado exitosamente", form)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Formulario no encontrado")));
    }

    @GetMapping("/process/{processKey}")
    public ResponseEntity<ApiResponse<List<FormDefinitionResponseDto>>> listarPorProceso(@PathVariable String processKey) {
        log.info("Solicitud GET /api/forms/process/{}", processKey);
        return ResponseEntity.ok(
                ApiResponse.success("Formularios listados exitosamente", formDefinitionService.listarPorProceso(processKey)));
    }
}
