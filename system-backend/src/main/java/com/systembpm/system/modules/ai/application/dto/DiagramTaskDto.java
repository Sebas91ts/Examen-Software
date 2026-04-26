package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagramTaskDto {
    private String id;
    private String name;
    private String area;
}
