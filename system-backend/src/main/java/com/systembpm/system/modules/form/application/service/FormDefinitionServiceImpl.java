package com.systembpm.system.modules.form.application.service;

import com.systembpm.system.modules.form.application.dto.FormDefinitionCreateDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionUpdateDto;
import com.systembpm.system.modules.form.application.dto.FormFieldDefinitionDto;
import com.systembpm.system.modules.form.domain.FormDefinition;
import com.systembpm.system.modules.form.domain.FormFieldDefinition;
import com.systembpm.system.modules.form.infrastructure.repository.FormDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionServiceImpl implements IFormDefinitionService {

    private final FormDefinitionRepository formDefinitionRepository;

    @Override
    public FormDefinitionResponseDto crear(FormDefinitionCreateDto dto) {
        validarDto(dto.getProcessKey(), dto.getProcessVersion(), dto.getTaskDefinitionKey(), dto.getTitle(), dto.getFields());
        validarNoDuplicado(dto.getProcessKey(), dto.getProcessVersion(), dto.getTaskDefinitionKey(), null);

        FormDefinition formDefinition = FormDefinition.builder()
                .processKey(normalizar(dto.getProcessKey()))
                .processVersion(dto.getProcessVersion())
                .taskDefinitionKey(normalizar(dto.getTaskDefinitionKey()))
                .title(dto.getTitle().trim())
                .fields(mapFields(dto.getFields()))
                .active(dto.getActive() == null || dto.getActive())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponse(formDefinitionRepository.save(formDefinition));
    }

    @Override
    public Optional<FormDefinitionResponseDto> obtenerPorClave(String processKey, Integer version, String taskDefinitionKey) {
        if (processKey == null || processKey.isBlank() || version == null || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("processKey, version y taskDefinitionKey son obligatorios");
        }

        return formDefinitionRepository
                .findByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        normalizar(processKey), version, normalizar(taskDefinitionKey))
                .map(this::toResponse);
    }

    @Override
    public Optional<FormDefinitionResponseDto> actualizar(String id, FormDefinitionUpdateDto dto) {
        validarDto(dto.getProcessKey(), dto.getProcessVersion(), dto.getTaskDefinitionKey(), dto.getTitle(), dto.getFields());

        FormDefinition existing = formDefinitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Formulario no encontrado con ID: " + id));

        validarNoDuplicado(dto.getProcessKey(), dto.getProcessVersion(), dto.getTaskDefinitionKey(), id);

        existing.setProcessKey(normalizar(dto.getProcessKey()));
        existing.setProcessVersion(dto.getProcessVersion());
        existing.setTaskDefinitionKey(normalizar(dto.getTaskDefinitionKey()));
        existing.setTitle(dto.getTitle().trim());
        existing.setFields(mapFields(dto.getFields()));
        existing.setActive(dto.getActive() == null || dto.getActive());
        existing.setUpdatedAt(LocalDateTime.now());

        return Optional.of(toResponse(formDefinitionRepository.save(existing)));
    }

    @Override
    public List<FormDefinitionResponseDto> listarPorProceso(String processKey) {
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("El processKey es obligatorio");
        }

        return formDefinitionRepository.findByProcessKeyIgnoreCaseOrderByProcessVersionAsc(normalizar(processKey)).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validarDto(String processKey, Integer version, String taskDefinitionKey, String title, List<FormFieldDefinitionDto> fields) {
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("El processKey es obligatorio");
        }
        if (version == null || version < 1) {
            throw new IllegalArgumentException("La version del proceso debe ser mayor o igual a 1");
        }
        if (taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("El taskDefinitionKey es obligatorio");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El titulo del formulario es obligatorio");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Debes definir al menos un campo");
        }

        List<Integer> orders = fields.stream().map(FormFieldDefinitionDto::getOrder).toList();
        if (orders.stream().anyMatch(order -> order == null)) {
            throw new IllegalArgumentException("Cada campo debe tener un orden");
        }
        if (orders.size() != orders.stream().distinct().count()) {
            throw new IllegalArgumentException("No puede haber campos con el mismo orden");
        }

        List<String> invalidTypes = fields.stream()
                .map(FormFieldDefinitionDto::getType)
                .filter(type -> type == null || !List.of("text", "textarea", "number", "date", "select", "file").contains(type))
                .toList();
        if (!invalidTypes.isEmpty()) {
            throw new IllegalArgumentException("El tipo de campo no es valido");
        }
    }

    private void validarNoDuplicado(String processKey, Integer version, String taskDefinitionKey, String currentId) {
        Optional<FormDefinition> duplicate = formDefinitionRepository
                .findByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        normalizar(processKey), version, normalizar(taskDefinitionKey));

        if (duplicate.isPresent() && (currentId == null || !duplicate.get().getId().equals(currentId))) {
            throw new IllegalArgumentException("Ya existe un formulario para ese processKey, version y taskDefinitionKey");
        }
    }

    private List<FormFieldDefinition> mapFields(List<FormFieldDefinitionDto> fields) {
        return fields.stream()
                .sorted(Comparator.comparing(FormFieldDefinitionDto::getOrder))
                .map(field -> FormFieldDefinition.builder()
                        .name(field.getName().trim())
                        .label(field.getLabel().trim())
                        .type(field.getType().trim())
                        .required(Boolean.TRUE.equals(field.getRequired()))
                        .placeholder(field.getPlaceholder())
                        .helpText(field.getHelpText())
                        .order(field.getOrder())
                        .options(field.getOptions() == null ? List.of() : field.getOptions())
                        .build())
                .toList();
    }

    private FormDefinitionResponseDto toResponse(FormDefinition formDefinition) {
        return FormDefinitionResponseDto.builder()
                .id(formDefinition.getId())
                .processKey(formDefinition.getProcessKey())
                .processVersion(formDefinition.getProcessVersion())
                .taskDefinitionKey(formDefinition.getTaskDefinitionKey())
                .title(formDefinition.getTitle())
                .fields(formDefinition.getFields().stream()
                        .map(this::toFieldDto)
                        .sorted(Comparator.comparing(FormFieldDefinitionDto::getOrder))
                        .toList())
                .active(formDefinition.getActive())
                .createdAt(formDefinition.getCreatedAt())
                .updatedAt(formDefinition.getUpdatedAt())
                .build();
    }

    private FormFieldDefinitionDto toFieldDto(FormFieldDefinition field) {
        return FormFieldDefinitionDto.builder()
                .name(field.getName())
                .label(field.getLabel())
                .type(field.getType())
                .required(field.getRequired())
                .placeholder(field.getPlaceholder())
                .helpText(field.getHelpText())
                .order(field.getOrder())
                .options(field.getOptions())
                .build();
    }

    private String normalizar(String value) {
        return value == null ? null : value.trim();
    }
}
