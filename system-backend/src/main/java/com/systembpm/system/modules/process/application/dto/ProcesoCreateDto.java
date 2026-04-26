package com.systembpm.system.modules.process.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para crear un proceso BPMN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoCreateDto {

    @NotBlank(message = "El nombre del proceso es obligatorio")
    private String nombre;

    @NotBlank(message = "El XML BPMN es obligatorio")
    private String xml;

    private String lastSavedBy;
}
