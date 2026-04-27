package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAnalysisSuggestionDto {
    private String id;
    private String analysisId;
    private String processId;
    private String processKey;
    private Integer processVersion;
    private String title;
    private String description;
    private String impact;
    private String relatedElementId;
    private Boolean canBeAppliedAutomatically;
    private String proposedXml;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private String decidedBy;
}
