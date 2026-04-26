package com.systembpm.system.modules.processtracking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveProcessTaskDto {

    private String id;
    private String taskDefinitionKey;
    private String taskName;
    private String areaId;
    private String areaNombre;
    private String assignedTo;
    private String createdAt;
}
