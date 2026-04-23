package com.systembpm.system.modules.form.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldOptionDefinitionDto {

    @NotBlank(message = "La etiqueta de la opcion es obligatoria")
    private String label;

    @NotBlank(message = "El valor de la opcion es obligatorio")
    private String value;
}
