package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentRuntimeSummaryDto {
    private int total;
    private int completed;
    private int missingRequired;
    private int editable;
    private int pendingReview;
}
