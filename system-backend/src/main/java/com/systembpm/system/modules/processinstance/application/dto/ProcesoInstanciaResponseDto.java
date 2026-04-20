package com.systembpm.system.modules.processinstance.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoInstanciaResponseDto {
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
