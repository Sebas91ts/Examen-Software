package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentRuntimeResponseDto {
    private String processKey;
    private Integer processVersion;
    private String processInstanceId;
    private String taskDefinitionKey;
    private String taskInstanceId;
    private List<TaskDocumentRuntimeRequirementDto> requirements;
    private TaskDocumentRuntimeSummaryDto summary;
}
