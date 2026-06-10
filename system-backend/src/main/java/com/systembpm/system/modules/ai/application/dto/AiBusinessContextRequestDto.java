package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBusinessContextRequestDto {
    @NotBlank
    private String message;
    private String taskId;
    private String processInstanceId;
    private String processKey;
    private String documentId;
    private String formId;
    private Map<String, Object> currentFormValues;
}
