package com.systembpm.system.modules.client.application.dto;

import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProcessStartPreviewDto {
    private String processId;
    private String processKey;
    private Integer processVersion;
    private String processName;
    private String firstTaskDefinitionKey;
    private String firstTaskName;
    private String firstTaskAreaId;
    private String firstTaskAreaName;
    private FormDefinitionResponseDto formDefinition;
}
