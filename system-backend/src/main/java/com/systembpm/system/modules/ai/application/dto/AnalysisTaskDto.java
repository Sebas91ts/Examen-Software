package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalysisTaskDto {

    @NotBlank(message = "El nombre de la tarea es obligatorio")
    private String name;

    @Min(value = 0, message = "La duracion debe ser mayor o igual a 0")
    private double durationMinutes;

    @NotBlank(message = "El area es obligatoria")
    private String area;
}
