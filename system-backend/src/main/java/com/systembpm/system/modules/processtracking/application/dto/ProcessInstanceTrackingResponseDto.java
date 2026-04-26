package com.systembpm.system.modules.processtracking.application.dto;

import com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstanceTrackingResponseDto {

    private String processInstanceId;
    private String processDefinitionId;
    private String processKey;
    private Integer processVersion;
    private String nombreProceso;
    private String estado;
    private String currentTaskName;
    private String currentAreaNombre;
    private String currentAssignedTo;
    private List<ActiveProcessTaskDto> activeTasks;
    private List<TaskExecutionLogResponseDto> history;
    private String xmlBpmn;
    private List<String> completedTaskKeys;
    private List<String> activeTaskKeys;
    private List<String> pendingTaskKeys;
}
