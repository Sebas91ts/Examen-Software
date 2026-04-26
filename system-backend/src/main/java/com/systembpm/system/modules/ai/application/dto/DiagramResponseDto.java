package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramResponseDto {
    private String processName;
    private List<DiagramTaskDto> tasks;
    private List<DiagramGatewayDto> gateways;
    private List<DiagramFlowDto> flows;
    private List<String> areas;
}
