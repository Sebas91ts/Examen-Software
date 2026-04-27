package com.systembpm.system.modules.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "process_ai_suggestions")
public class ProcessAiAnalysisSuggestion {
    @Id
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
    private ProcessAiSuggestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private String decidedBy;
}
