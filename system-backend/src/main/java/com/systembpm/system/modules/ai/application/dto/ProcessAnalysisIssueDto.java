package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAnalysisIssueDto {
    private String type;
    private String description;
    private String elementId;
    private String severity;
}
