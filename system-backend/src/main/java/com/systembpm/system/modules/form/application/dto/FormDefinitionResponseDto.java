package com.systembpm.system.modules.form.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinitionResponseDto {
    private String id;
    private String processKey;
    private Integer processVersion;
    private String taskDefinitionKey;
    private String title;
    private List<FormFieldDefinitionDto> fields;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
