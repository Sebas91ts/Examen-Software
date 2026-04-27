package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAnalysisResponseDto {
    private String id;
    private String processId;
    private String processKey;
    private Integer processVersion;
    private String processName;
    private String summary;
    private Integer score;
    private List<ProcessAnalysisIssueDto> issues;
    private List<ProcessAnalysisSuggestionDto> suggestions;
    private String status;
    private LocalDateTime createdAt;
}
