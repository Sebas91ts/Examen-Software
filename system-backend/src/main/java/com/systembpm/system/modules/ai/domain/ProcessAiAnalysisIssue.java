package com.systembpm.system.modules.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAiAnalysisIssue {
    private String type;
    private String description;
    private String elementId;
    private String severity;
}
