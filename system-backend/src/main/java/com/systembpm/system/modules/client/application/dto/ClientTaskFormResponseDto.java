package com.systembpm.system.modules.client.application.dto;

import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTaskFormResponseDto {
    private String taskId;
    private String taskName;
    private String processInstanceId;
    private String processKey;
    private Integer processVersion;
    private String processName;
    private String areaName;
    private String assignee;
    private FormDefinitionResponseDto formDefinition;
    private Map<String, Object> currentValues;
}
