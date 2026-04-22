package com.systembpm.system.modules.form.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinitionCreateDto {

    @NotBlank(message = "El processKey es obligatorio")
    private String processKey;

    @NotNull(message = "La version del proceso es obligatoria")
    @Min(value = 1, message = "La version debe ser mayor o igual a 1")
    private Integer processVersion;

    @NotBlank(message = "El taskDefinitionKey es obligatorio")
    private String taskDefinitionKey;

    @NotBlank(message = "El titulo del formulario es obligatorio")
    private String title;

    @NotEmpty(message = "Debes definir al menos un campo")
    @Valid
    private List<FormFieldDefinitionDto> fields;

    private Boolean active;
}
