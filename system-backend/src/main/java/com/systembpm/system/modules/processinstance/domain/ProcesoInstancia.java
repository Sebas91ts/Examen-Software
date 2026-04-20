package com.systembpm.system.modules.processinstance.domain;

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
@Document(collection = "proceso_instancias")
public class ProcesoInstancia {

    @Id
    private String id;

    private String processDefinitionId;
    private String processKey;
    private Integer version;
    private String nombreProceso;
    private String estado;
    private String currentElementId;
    private String iniciadoPor;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
