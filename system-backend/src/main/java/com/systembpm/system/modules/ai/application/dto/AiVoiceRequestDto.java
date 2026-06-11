package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiVoiceRequestDto {
    @NotBlank
    private String transcript;
    private String taskId;
    private String processInstanceId;
    private String processKey;
    private String documentId;
    private String formId;
}
