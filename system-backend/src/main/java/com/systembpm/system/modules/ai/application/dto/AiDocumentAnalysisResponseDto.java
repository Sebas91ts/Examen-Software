package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentAnalysisResponseDto {
    private String documentType;
    private Double confidence;
    private String executiveSummary;
    private String shortSummary;
    private String detailedSummary;
    private List<String> keywords;
    private List<String> suggestedTags;
    private Map<String, Object> entities;
}
