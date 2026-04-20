package com.systembpm.system.modules.area.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AreaUpdateDto {

    @NotBlank(message = "El nombre del area es obligatorio")
    private String nombre;

    private String descripcion;

    private Boolean activa;
}
