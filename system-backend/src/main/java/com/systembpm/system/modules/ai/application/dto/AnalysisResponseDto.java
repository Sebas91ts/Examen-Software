package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalysisResponseDto {
    private double averageTimeMinutes;
    private List<AnalysisTaskDto> slowestTasks;
    private List<AnalysisAreaDto> overloadedAreas;
}
