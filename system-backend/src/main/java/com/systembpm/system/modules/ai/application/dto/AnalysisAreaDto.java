package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalysisAreaDto {
    private String area;
    private double totalMinutes;
}
