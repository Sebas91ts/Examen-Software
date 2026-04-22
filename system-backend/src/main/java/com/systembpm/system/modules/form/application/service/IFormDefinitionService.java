package com.systembpm.system.modules.form.application.service;

import com.systembpm.system.modules.form.application.dto.FormDefinitionCreateDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionUpdateDto;

import java.util.List;
import java.util.Optional;

public interface IFormDefinitionService {
    FormDefinitionResponseDto crear(FormDefinitionCreateDto dto);

    Optional<FormDefinitionResponseDto> obtenerPorClave(String processKey, Integer version, String taskDefinitionKey);

    Optional<FormDefinitionResponseDto> actualizar(String id, FormDefinitionUpdateDto dto);

    List<FormDefinitionResponseDto> listarPorProceso(String processKey);
}
