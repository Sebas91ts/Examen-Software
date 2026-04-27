package com.systembpm.system.modules.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAiAnalysisSuggestion {
    private String title;
    private String description;
    private String impact;
    private String relatedElementId;
    private Boolean canBeAppliedAutomatically;
}
