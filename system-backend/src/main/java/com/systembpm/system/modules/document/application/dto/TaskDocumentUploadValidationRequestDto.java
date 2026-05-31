package com.systembpm.system.modules.document.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentUploadValidationRequestDto {

    @NotBlank(message = "processKey es obligatorio")
    private String processKey;

    @NotNull(message = "processVersion es obligatorio")
    private Integer processVersion;

    @NotBlank(message = "taskDefinitionKey es obligatorio")
    private String taskDefinitionKey;

    @NotBlank(message = "processInstanceId es obligatorio")
    private String processInstanceId;

    private String mimeType;

    private Long size;
}

