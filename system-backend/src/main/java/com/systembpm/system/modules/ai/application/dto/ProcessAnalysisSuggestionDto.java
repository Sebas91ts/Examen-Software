package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAnalysisSuggestionDto {
    private String title;
    private String description;
    private String impact;
    private String relatedElementId;
    private Boolean canBeAppliedAutomatically;
}
