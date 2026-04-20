package com.systembpm.system.modules.processinstance.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoInstanciaCreateDto {
    private String processDefinitionId;
}
