package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalysisRequestDto {

    @NotEmpty(message = "Debe enviar al menos una tarea")
    @Valid
    private List<AnalysisTaskDto> tasks;
}
