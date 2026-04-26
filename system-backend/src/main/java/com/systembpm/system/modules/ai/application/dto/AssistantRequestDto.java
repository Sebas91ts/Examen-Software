package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantRequestDto {

    @NotBlank(message = "El mensaje es obligatorio")
    private String message;
}
