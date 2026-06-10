package com.systembpm.system.modules.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentAnalysisRequestDto {
    @NotBlank
    private String text;
    private String documentId;
    private String analysisType;
    private Map<String, Object> context;
}
