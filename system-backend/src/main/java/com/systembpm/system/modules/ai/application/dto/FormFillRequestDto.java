package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class FormFillRequestDto {

    @NotBlank(message = "La transcripción es obligatoria")
    private String transcript;

    private String processName;
    private String taskName;
    private String areaName;
    private Map<String, Object> currentValues;

    @Valid
    private List<FormFillFieldContextDto> fields;
}
