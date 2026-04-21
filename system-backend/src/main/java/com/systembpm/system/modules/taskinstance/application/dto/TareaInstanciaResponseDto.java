package com.systembpm.system.modules.taskinstance.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaInstanciaResponseDto {
    private String id;
    private String processInstanceId;
    private String processDefinitionId;
    private String nombreProceso;
    private String taskDefinitionKey;
    private String nombreTarea;
    private String areaId;
    private String areaNombre;
    private String estado;
    private String assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
