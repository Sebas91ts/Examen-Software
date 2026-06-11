package com.systembpm.system.modules.document.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequestDto {

    @NotBlank(message = "tenantId es obligatorio")
    private String tenantId;

    @NotBlank(message = "processInstanceId es obligatorio")
    private String processInstanceId;

    /**
     * Contexto BPM opcional. Si viene presente, se aplican restricciones documentales por tarea.
     * Se deja opcional para compatibilidad con clientes existentes.
     */
    private String processKey;

    private Integer processVersion;

    private String taskDefinitionKey;

    private String taskInstanceId;

    private String documentRequirementId;

    private String folderId;
}
