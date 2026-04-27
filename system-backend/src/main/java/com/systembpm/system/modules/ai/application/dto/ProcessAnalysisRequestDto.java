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
public class ProcessAnalysisRequestDto {
    @NotBlank
    private String processXml;
    private String processName;
    private String processId;
    private String processKey;
    private Integer processVersion;
    private Map<String, Object> metrics;
}
