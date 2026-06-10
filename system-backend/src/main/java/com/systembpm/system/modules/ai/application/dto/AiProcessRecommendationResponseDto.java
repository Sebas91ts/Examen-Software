package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProcessRecommendationResponseDto {
    private String intent;
    private String processKey;
    private Double confidence;
    private String reason;
    private List<String> requiredDocuments;
    private List<String> requiredForms;
    private List<String> alternatives;
}
