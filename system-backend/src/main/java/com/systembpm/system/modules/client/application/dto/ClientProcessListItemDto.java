package com.systembpm.system.modules.client.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProcessListItemDto {
    private String processId;
    private String processKey;
    private String nombre;
    private Integer version;
    private String descripcion;
}
