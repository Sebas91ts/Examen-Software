package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAssistResponseDto {
    private String response;
    private String intent;
    private Double confidence;
    private List<String> suggestedActions;
    private Map<String, Object> structuredData;
}
