package com.systembpm.system.modules.process.application.service;

import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.form.domain.FormDefinition;
import com.systembpm.system.modules.form.domain.FormFieldDefinition;
import com.systembpm.system.modules.form.infrastructure.repository.FormDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementacion del servicio de procesos BPMN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesoServiceImpl implements IProcesoService {

    private static final String ESTADO_BORRADOR = "BORRADOR";
    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String ESTADO_HISTORICO = "HISTORICO";
    private static final String CREATED_BY_DEFAULT = "admin";

    private final ProcesoRepository procesoRepository;
    private final CamundaService camundaService;
    private final FormDefinitionRepository formDefinitionRepository;

    @Override
    public Proceso guardar(ProcesoCreateDto dto) {
        log.info("Guardando proceso BPMN con nombre: {}", dto.getNombre());

        validarDto(dto);
        String nombreNormalizado = normalizarNombre(dto.getNombre());
        String processKey = generarProcessKey(nombreNormalizado);

        boolean existe = procesoRepository
                .findByNombreIgnoreCase(nombreNormalizado)
                .isPresent();

        if (existe) {
            throw new IllegalArgumentException("Ya existe un proceso con ese nombre");
        }

        Proceso proceso = Proceso.builder()
                .nombre(nombreNormalizado)
                .xml(dto.getXml().trim())
                .version(1)
                .estado(ESTADO_BORRADOR)
                .createdBy(CREATED_BY_DEFAULT)
                .processKey(processKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Proceso procesoGuardado = procesoRepository.save(proceso);
        log.info("Proceso BPMN guardado exitosamente con ID: {}", procesoGuardado.getId());

        return procesoGuardado;
    }

    @Override
    public List<Proceso> listar() {
        log.info("Listando procesos BPMN");
        return procesoRepository.findAll();
    }

    @Override
    public Proceso obtenerPorId(String id) {
        log.info("Buscando proceso BPMN por ID: {}", id);

        return procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));
    }

    @Override
    public Proceso actualizar(String id, ProcesoCreateDto dto) {
        log.info("Actualizando proceso BPMN con ID: {}", id);

        validarDto(dto);

        Proceso procesoExistente = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        validarEditable(procesoExistente);

        procesoExistente.setNombre(normalizarNombre(dto.getNombre()));
        procesoExistente.setXml(dto.getXml().trim());
        procesoExistente.setUpdatedAt(LocalDateTime.now());

        Proceso procesoActualizado = procesoRepository.save(procesoExistente);
        log.info("Proceso BPMN actualizado exitosamente con ID: {}", procesoActualizado.getId());

        return procesoActualizado;
    }

    @Override
    public Proceso publicar(String id) {
        log.info("Publicando proceso BPMN con ID: {}", id);

        Proceso proceso = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        List<Proceso> procesosMismaClave = procesoRepository.findByProcessKey(proceso.getProcessKey());
        LocalDateTime now = LocalDateTime.now();

        for (Proceso procesoRelacionado : procesosMismaClave) {
            if (!procesoRelacionado.getId().equals(proceso.getId())
                    && ESTADO_PUBLICADO.equalsIgnoreCase(procesoRelacionado.getEstado())) {
                procesoRelacionado.setEstado(ESTADO_HISTORICO);
                procesoRelacionado.setUpdatedAt(now);
                procesoRepository.save(procesoRelacionado);
                log.info("Proceso BPMN previo marcado como HISTORICO. ID: {}", procesoRelacionado.getId());
            }
        }

        proceso.setEstado(ESTADO_PUBLICADO);
        proceso.setUpdatedAt(now);

        Proceso procesoPublicado = procesoRepository.save(proceso);
        log.info("Proceso BPMN publicado exitosamente con ID: {}", procesoPublicado.getId());

        return procesoPublicado;
    }

    @Override
    public Proceso publicarYDesplegar(String id) {
        log.info("Publicando y desplegando proceso BPMN con ID: {}", id);

        Proceso proceso = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        camundaService.desplegarProceso(proceso.getId());
        return marcarComoPublicado(proceso);
    }

    @Override
    public Proceso crearNuevaVersion(String id) {
        log.info("Creando nueva version del proceso BPMN con ID: {}", id);

        Proceso procesoOrigen = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        String processKey = normalizarProcessKey(
                procesoOrigen.getProcessKey() != null && !procesoOrigen.getProcessKey().isBlank()
                        ? procesoOrigen.getProcessKey()
                        : procesoOrigen.getNombre());

        Integer ultimaVersion = procesoRepository.findTopByProcessKeyOrderByVersionDesc(processKey)
                .map(Proceso::getVersion)
                .orElse(procesoOrigen.getVersion() != null ? procesoOrigen.getVersion() : 1);

        Proceso nuevaVersion = Proceso.builder()
                .nombre(normalizarNombre(
                        procesoOrigen.getNombre() != null && !procesoOrigen.getNombre().isBlank()
                                ? procesoOrigen.getNombre()
                                : processKey))
                .xml(procesoOrigen.getXml())
                .version(ultimaVersion + 1)
                .estado(ESTADO_BORRADOR)
                .createdBy(CREATED_BY_DEFAULT)
                .processKey(processKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Proceso procesoGuardado = procesoRepository.save(nuevaVersion);
        clonarFormulariosDeVersionAnterior(procesoOrigen, procesoGuardado);
        log.info("Nueva version creada exitosamente con ID: {}", procesoGuardado.getId());

        return procesoGuardado;
    }

    private void clonarFormulariosDeVersionAnterior(Proceso procesoOrigen, Proceso nuevaVersion) {
        if (procesoOrigen == null || nuevaVersion == null) {
            return;
        }

        String processKey = nuevaVersion.getProcessKey();
        Integer versionAnterior = procesoOrigen.getVersion();
        Integer nuevaVersionNumero = nuevaVersion.getVersion();

        if (processKey == null || processKey.isBlank() || versionAnterior == null || nuevaVersionNumero == null) {
            return;
        }

        List<FormDefinition> formulariosOrigen = formDefinitionRepository
                .findByProcessKeyIgnoreCaseOrderByProcessVersionAsc(processKey)
                .stream()
                .filter(form -> versionAnterior.equals(form.getProcessVersion()))
                .toList();

        if (formulariosOrigen.isEmpty()) {
            return;
        }

        List<FormDefinition> formulariosClonados = formulariosOrigen.stream()
                .filter(form -> !formDefinitionRepository.existsByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        processKey,
                        nuevaVersionNumero,
                        form.getTaskDefinitionKey()))
                .map(form -> FormDefinition.builder()
                        .processKey(form.getProcessKey())
                        .processVersion(nuevaVersionNumero)
                        .taskDefinitionKey(form.getTaskDefinitionKey())
                        .title(form.getTitle())
                        .fields(clonarCampos(form.getFields()))
                        .active(form.getActive())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        if (!formulariosClonados.isEmpty()) {
            formDefinitionRepository.saveAll(formulariosClonados);
            log.info("Se clonaron {} formularios de la version {} a la version {}", formulariosClonados.size(), versionAnterior, nuevaVersionNumero);
        }
    }

    private List<FormFieldDefinition> clonarCampos(List<FormFieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }

        return fields.stream()
                .map(field -> FormFieldDefinition.builder()
                        .name(field.getName())
                        .label(field.getLabel())
                        .type(field.getType())
                        .required(field.getRequired())
                        .placeholder(field.getPlaceholder())
                        .helpText(field.getHelpText())
                        .order(field.getOrder())
                        .options(field.getOptions() == null ? List.of() : List.copyOf(field.getOptions()))
                        .build())
                .toList();
    }

    private void validarDto(ProcesoCreateDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos del proceso son obligatorios");
        }

        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso es obligatorio");
        }

        if (dto.getXml() == null || dto.getXml().isBlank()) {
            throw new IllegalArgumentException("El XML BPMN no puede estar vacio");
        }

        if (!dto.getXml().contains("<bpmn:")) {
            throw new IllegalArgumentException("El XML proporcionado no parece ser un BPMN valido");
        }
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Proceso sin nombre";
        }

        return nombre.trim().replaceAll("\\s+", " ");
    }

    private String generarProcessKey(String nombre) {
        return normalizarProcessKey(nombre);
    }

    private String normalizarProcessKey(String source) {
        String key = Normalizer.normalize(source.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9\\s_]", "")
                .replaceAll("[\\s_]+", "_")
                .replaceAll("^_+|_+$", "");

        return key.isBlank() ? "proceso_sin_nombre" : key;
    }

    private void validarEditable(Proceso proceso) {
        if (ESTADO_PUBLICADO.equalsIgnoreCase(proceso.getEstado())) {
            throw new IllegalArgumentException("No se puede editar un proceso publicado");
        }
    }

    private Proceso marcarComoPublicado(Proceso proceso) {
        List<Proceso> procesosMismaClave = procesoRepository.findByProcessKey(proceso.getProcessKey());
        LocalDateTime now = LocalDateTime.now();

        for (Proceso procesoRelacionado : procesosMismaClave) {
            if (!procesoRelacionado.getId().equals(proceso.getId())
                    && ESTADO_PUBLICADO.equalsIgnoreCase(procesoRelacionado.getEstado())) {
                procesoRelacionado.setEstado(ESTADO_HISTORICO);
                procesoRelacionado.setUpdatedAt(now);
                procesoRepository.save(procesoRelacionado);
                log.info("Proceso BPMN previo marcado como HISTORICO. ID: {}", procesoRelacionado.getId());
            }
        }

        proceso.setEstado(ESTADO_PUBLICADO);
        proceso.setUpdatedAt(now);

        Proceso procesoPublicado = procesoRepository.save(proceso);
        log.info("Proceso BPMN publicado exitosamente con ID: {}", procesoPublicado.getId());

        return procesoPublicado;
    }
}
