package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditDiagramRequestDto {

    @NotBlank(message = "La instruccion es obligatoria")
    private String instruction;

    @NotBlank(message = "El XML actual es obligatorio")
    private String currentXml;
}
