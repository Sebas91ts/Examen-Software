package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiagramResponseDto {
    private List<DiagramTaskDto> tasks;
    private List<DiagramFlowDto> flows;
    private List<DiagramAreaDto> areas;
}
