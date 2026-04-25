package com.systembpm.system.modules.taskexecutionlog.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_execution_logs")
public class TaskExecutionLog {

    @Id
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
