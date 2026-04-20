package com.systembpm.system.modules.area.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AreaCreateDto {

    @NotBlank(message = "El nombre del area es obligatorio")
    private String nombre;

    private String descripcion;
}
