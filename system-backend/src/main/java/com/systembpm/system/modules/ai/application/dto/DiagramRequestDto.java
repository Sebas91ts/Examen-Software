package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagramRequestDto {

    @NotBlank(message = "El texto es obligatorio")
    private String text;
}
