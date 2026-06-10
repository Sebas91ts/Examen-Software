package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportPlanResponseDto {
    private String reportType;
    private Map<String, Object> filters;
    private String groupBy;
    private String format;
    private List<String> columns;
    private Double confidence;
    private String reason;
}
