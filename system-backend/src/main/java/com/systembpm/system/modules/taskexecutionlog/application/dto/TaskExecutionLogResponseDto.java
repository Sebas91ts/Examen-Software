package com.systembpm.system.modules.taskexecutionlog.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLogResponseDto {

    private String id;
    private String processInstanceId;
    private String processDefinitionId;
    private String processKey;
    private Integer processVersion;
    private String taskDefinitionKey;
    private String taskName;
    private String areaId;
    private String areaNombre;
    private String assignedTo;
    private String completedBy;
    private Map<String, Object> formData;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
