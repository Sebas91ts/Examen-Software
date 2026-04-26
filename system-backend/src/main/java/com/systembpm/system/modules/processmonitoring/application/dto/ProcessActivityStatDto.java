package com.systembpm.system.modules.processmonitoring.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessActivityStatDto {

    private String taskDefinitionKey;
    private String taskName;
    private long activeInstances;
}
