package com.systembpm.system.modules.processmonitoring.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMonitoringResponseDto {

    private String processId;
    private String processKey;
    private Integer processVersion;
    private String nombreProceso;
    private String estado;
    private String xmlBpmn;
    private long totalActiveInstances;
    private List<ProcessActivityStatDto> activityStats;
    private List<ProcessMonitorInstanceDto> instances;
}
