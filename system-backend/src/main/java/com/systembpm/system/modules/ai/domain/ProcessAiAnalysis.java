package com.systembpm.system.modules.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "process_ai_analyses")
public class ProcessAiAnalysis {
    @Id
    private String id;
    private String processId;
    private String processKey;
    private Integer processVersion;
    private String processName;
    private Integer score;
    private String summary;
    private List<ProcessAiAnalysisIssue> issues;
    private List<ProcessAiAnalysisSuggestion> suggestions;
    private ProcessAiAnalysisStatus status;
    private String fingerprint;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
}
