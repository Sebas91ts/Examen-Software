package com.systembpm.system.modules.area.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AreaResponseDto {

    private String id;
    private String nombre;
    private String descripcion;
    private Boolean activa;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
