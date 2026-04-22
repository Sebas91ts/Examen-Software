package com.systembpm.system.modules.form.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldDefinitionDto {

    @NotBlank(message = "El nombre del campo es obligatorio")
    private String name;

    @NotBlank(message = "La etiqueta del campo es obligatoria")
    private String label;

    @NotBlank(message = "El tipo del campo es obligatorio")
    @Pattern(regexp = "text|textarea|number|date|select", message = "El tipo de campo no es valido")
    private String type;

    @NotNull(message = "El atributo required es obligatorio")
    private Boolean required;

    private String placeholder;

    private String helpText;

    @NotNull(message = "El orden del campo es obligatorio")
    private Integer order;

    private List<String> options;
}
