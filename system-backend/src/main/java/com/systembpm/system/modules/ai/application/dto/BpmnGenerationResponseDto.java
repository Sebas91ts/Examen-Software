package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BpmnGenerationResponseDto {
    private String processName;
    private String processKey;
    private String xml;
}
