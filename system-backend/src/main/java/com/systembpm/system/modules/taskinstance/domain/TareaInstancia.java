package com.systembpm.system.modules.taskinstance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tarea_instancias")
public class TareaInstancia {

    @Id
    private String id;

    private String processInstanceId;
    private String processDefinitionId;
    private String taskDefinitionKey;
    private String nombreTarea;
    private String areaId;
    private String areaNombre;
    private String estado;
    private String assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
