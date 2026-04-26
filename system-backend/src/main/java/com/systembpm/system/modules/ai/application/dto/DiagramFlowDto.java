package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagramFlowDto {
    private String fromTask;
    private String toTask;
}
