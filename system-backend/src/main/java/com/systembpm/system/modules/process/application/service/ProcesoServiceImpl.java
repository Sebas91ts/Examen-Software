package com.systembpm.system.modules.process.application.service;

import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.bpmn.application.service.BpmnXmlSanitizerService;
import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.form.domain.FormDefinition;
import com.systembpm.system.modules.form.domain.FormFieldDefinition;
import com.systembpm.system.modules.form.domain.FormFieldOptionDefinition;
import com.systembpm.system.modules.form.infrastructure.repository.FormDefinitionRepository;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.DocumentRequirement;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.domain.TaskDocumentPermissions;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final BpmnXmlSanitizerService bpmnXmlSanitizerService;

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
                .descripcion(normalizarDescripcion(dto.getDescripcion()))
                .xml(sanitizarXml(dto.getXml()))
                .version(1)
                .estado(ESTADO_BORRADOR)
                .clientStartEnabled(resolverClientStartEnabled(dto.getClientStartEnabled(), dto.getXml()))
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
    public List<Proceso> listarPublicados() {
        log.info("Listando procesos BPMN publicados");
        return procesoRepository.findByEstadoIgnoreCase(ESTADO_PUBLICADO);
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
        procesoExistente.setDescripcion(normalizarDescripcion(dto.getDescripcion()));
        procesoExistente.setXml(sanitizarXml(dto.getXml()));
        procesoExistente.setClientStartEnabled(resolverClientStartEnabled(dto.getClientStartEnabled(), dto.getXml()));
        procesoExistente.setUpdatedAt(LocalDateTime.now());
        procesoExistente.setLastSavedAt(procesoExistente.getUpdatedAt());
        procesoExistente.setLastSavedBy(normalizarUsuarioGuardado(dto.getLastSavedBy()));

        Proceso procesoActualizado = procesoRepository.save(procesoExistente);
        log.info("Proceso BPMN actualizado exitosamente con ID: {}", procesoActualizado.getId());

        return procesoActualizado;
    }

    @Override
    public Proceso autosave(String id, ProcesoCreateDto dto) {
        log.info("Autosave de proceso BPMN con ID: {}", id);

        validarDtoAutosave(dto);

        Proceso procesoExistente = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        validarEditable(procesoExistente);

        if (dto.getNombre() != null && !dto.getNombre().isBlank()) {
            procesoExistente.setNombre(normalizarNombre(dto.getNombre()));
        }

        LocalDateTime now = LocalDateTime.now();
        if (dto.getDescripcion() != null) {
            procesoExistente.setDescripcion(normalizarDescripcion(dto.getDescripcion()));
        }
        procesoExistente.setXml(sanitizarXml(dto.getXml()));
        procesoExistente.setClientStartEnabled(resolverClientStartEnabled(dto.getClientStartEnabled(), dto.getXml()));
        procesoExistente.setUpdatedAt(now);
        procesoExistente.setLastSavedAt(now);
        procesoExistente.setLastSavedBy(normalizarUsuarioGuardado(dto.getLastSavedBy()));

        Proceso procesoActualizado = procesoRepository.save(procesoExistente);
        log.info("Autosave de proceso BPMN aplicado exitosamente con ID: {}", procesoActualizado.getId());

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
                .descripcion(procesoOrigen.getDescripcion())
                .xml(sanitizarXml(procesoOrigen.getXml()))
                .version(ultimaVersion + 1)
                .estado(ESTADO_BORRADOR)
                .clientStartEnabled(procesoOrigen.isClientStartEnabled())
                .createdBy(CREATED_BY_DEFAULT)
                .processKey(processKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Proceso procesoGuardado = procesoRepository.save(nuevaVersion);
        clonarFormulariosDeVersionAnterior(procesoOrigen, procesoGuardado);
        clonarConfiguracionesDocumentalesDeVersionAnterior(procesoOrigen, procesoGuardado);
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

    private void clonarConfiguracionesDocumentalesDeVersionAnterior(Proceso procesoOrigen, Proceso nuevaVersion) {
        if (procesoOrigen == null || nuevaVersion == null) {
            return;
        }

        String processKey = nuevaVersion.getProcessKey();
        Integer versionAnterior = procesoOrigen.getVersion();
        Integer nuevaVersionNumero = nuevaVersion.getVersion();

        if (processKey == null || processKey.isBlank() || versionAnterior == null || nuevaVersionNumero == null) {
            return;
        }

        List<TaskDocumentConfig> configuracionesOrigen = taskDocumentConfigRepository
                .findByProcessKeyIgnoreCaseAndProcessVersion(processKey, versionAnterior);

        if (configuracionesOrigen.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<TaskDocumentConfig> configuracionesClonadas = configuracionesOrigen.stream()
                .filter(config -> !taskDocumentConfigRepository.existsByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        config.getTenantId(),
                        processKey,
                        nuevaVersionNumero,
                        config.getTaskDefinitionKey()))
                .map(config -> TaskDocumentConfig.builder()
                        .tenantId(config.getTenantId())
                        .processKey(config.getProcessKey())
                        .processVersion(nuevaVersionNumero)
                        .taskDefinitionKey(config.getTaskDefinitionKey())
                        .documentRequirements(clonarRequerimientosDocumentales(config.getDocumentRequirements()))
                        .documentName(config.getDocumentName())
                        .description(config.getDescription())
                        .documentDirection(config.getDocumentDirection())
                        .required(config.getRequired())
                        .allowMultipleFiles(config.getAllowMultipleFiles())
                        .allowUpload(config.getAllowUpload())
                        .editable(config.getEditable())
                        .allowEditing(config.getAllowEditing())
                        .collaborativeEditing(config.getCollaborativeEditing())
                        .allowVersioning(config.getAllowVersioning())
                        .allowedMimeTypes(copiarLista(config.getAllowedMimeTypes()))
                        .maxFileSizeBytes(config.getMaxFileSizeBytes())
                        .maxFiles(config.getMaxFiles())
                        .readOnlyAfterComplete(config.getReadOnlyAfterComplete())
                        .requireApproval(config.getRequireApproval())
                        .templateDocumentId(config.getTemplateDocumentId())
                        .permissions(clonarPermisosDocumentales(config.getPermissions()))
                        .ownerAreaId(config.getOwnerAreaId())
                        .allowedAreaIds(copiarLista(config.getAllowedAreaIds()))
                        .accessRules(clonarReglasDocumentales(config.getAccessRules()))
                        .shareWithNextArea(config.getShareWithNextArea())
                        .autoGenerateOnTaskStart(config.getAutoGenerateOnTaskStart())
                        .createdAt(now)
                        .updatedAt(now)
                        .createdBy(config.getCreatedBy())
                        .updatedBy(config.getUpdatedBy())
                        .build())
                .collect(Collectors.toList());

        if (!configuracionesClonadas.isEmpty()) {
            taskDocumentConfigRepository.saveAll(configuracionesClonadas);
            log.info("Se clonaron {} configuraciones documentales de la version {} a la version {}",
                    configuracionesClonadas.size(), versionAnterior, nuevaVersionNumero);
        }
    }

    private List<DocumentRequirement> clonarRequerimientosDocumentales(List<DocumentRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }

        return requirements.stream()
                .map(requirement -> DocumentRequirement.builder()
                        .id(requirement.getId())
                        .name(requirement.getName())
                        .description(requirement.getDescription())
                        .documentDirection(requirement.getDocumentDirection())
                        .required(requirement.getRequired())
                        .allowUpload(requirement.getAllowUpload())
                        .allowMultipleFiles(requirement.getAllowMultipleFiles())
                        .editable(requirement.getEditable())
                        .collaborativeEditing(requirement.getCollaborativeEditing())
                        .requireApproval(requirement.getRequireApproval())
                        .readOnlyAfterComplete(requirement.getReadOnlyAfterComplete())
                        .allowedMimeTypes(copiarLista(requirement.getAllowedMimeTypes()))
                        .maxFileSizeBytes(requirement.getMaxFileSizeBytes())
                        .maxFiles(requirement.getMaxFiles())
                        .ownerAreaId(requirement.getOwnerAreaId())
                        .allowedAreaIds(copiarLista(requirement.getAllowedAreaIds()))
                        .accessRules(clonarReglasDocumentales(requirement.getAccessRules()))
                        .documentLifecyclePolicy(requirement.getDocumentLifecyclePolicy())
                        .build())
                .toList();
    }

    private List<DocumentAreaAccessRule> clonarReglasDocumentales(List<DocumentAreaAccessRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        return rules.stream()
                .map(rule -> DocumentAreaAccessRule.builder()
                        .areaId(rule.getAreaId())
                        .canView(rule.getCanView())
                        .canUpload(rule.getCanUpload())
                        .canEdit(rule.getCanEdit())
                        .canDownload(rule.getCanDownload())
                        .canApprove(rule.getCanApprove())
                        .canReject(rule.getCanReject())
                        .canLock(rule.getCanLock())
                        .build())
                .toList();
    }

    private TaskDocumentPermissions clonarPermisosDocumentales(TaskDocumentPermissions permissions) {
        if (permissions == null) {
            return null;
        }

        return TaskDocumentPermissions.builder()
                .canView(permissions.getCanView())
                .canUpload(permissions.getCanUpload())
                .canEdit(permissions.getCanEdit())
                .canDelete(permissions.getCanDelete())
                .canApprove(permissions.getCanApprove())
                .canDownload(permissions.getCanDownload())
                .canReject(permissions.getCanReject())
                .canLock(permissions.getCanLock())
                .build();
    }

    private List<String> copiarLista(List<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
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
                        .optionItems(clonarOpciones(field.getOptionItems()))
                        .build())
                .toList();
    }

    private List<FormFieldOptionDefinition> clonarOpciones(List<FormFieldOptionDefinition> optionItems) {
        if (optionItems == null || optionItems.isEmpty()) {
            return List.of();
        }

        return optionItems.stream()
                .map(item -> FormFieldOptionDefinition.builder()
                        .label(item.getLabel())
                        .value(item.getValue())
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

    private void validarDtoAutosave(ProcesoCreateDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos del autosave son obligatorios");
        }

        if (dto.getXml() == null || dto.getXml().isBlank()) {
            throw new IllegalArgumentException("El XML BPMN no puede estar vacio");
        }

        if (!dto.getXml().contains("<bpmn:")) {
            throw new IllegalArgumentException("El XML proporcionado no parece ser un BPMN valido");
        }
    }

    private String normalizarUsuarioGuardado(String lastSavedBy) {
        if (lastSavedBy == null || lastSavedBy.isBlank()) {
            return CREATED_BY_DEFAULT;
        }

        return lastSavedBy.trim();
    }

    private String sanitizarXml(String xml) {
        return bpmnXmlSanitizerService.sanitize(xml == null ? null : xml.trim());
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Proceso sin nombre";
        }

        return nombre.trim().replaceAll("\\s+", " ");
    }

    private String normalizarDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }

        String cleaned = descripcion.trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
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
        if (ESTADO_PUBLICADO.equalsIgnoreCase(proceso.getEstado())
                || ESTADO_HISTORICO.equalsIgnoreCase(proceso.getEstado())) {
            throw new IllegalArgumentException("No se puede editar un proceso publicado o historico");
        }
    }

    private boolean resolverClientStartEnabled(Boolean requestedValue, String xml) {
        if (requestedValue != null) {
            return Boolean.TRUE.equals(requestedValue);
        }

        return detectarPrimerPasoCliente(xml);
    }

    private boolean detectarPrimerPasoCliente(String xml) {
        if (xml == null || xml.isBlank()) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String firstInteractiveNodeId = encontrarPrimerNodoInteractivoDesdeStart(document);
            if (firstInteractiveNodeId == null || firstInteractiveNodeId.isBlank()) {
                return false;
            }

            String taskId = firstInteractiveNodeId.trim();
            Element firstUserTask = encontrarElementoPorId(document, taskId);
            if (firstUserTask == null) {
                return false;
            }

            String localName = firstUserTask.getLocalName();
            if (localName == null || !esNodoTarea(localName)) {
                return false;
            }

            if (taskId == null || taskId.isBlank()) {
                return false;
            }

            NodeList lanes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "lane");
            for (int i = 0; i < lanes.getLength(); i++) {
                Element lane = (Element) lanes.item(i);
                if (!laneContieneNodo(lane, taskId.trim())) {
                    continue;
                }

                String laneName = lane.getAttribute("name");
                return laneName != null && laneName.trim().equalsIgnoreCase("Cliente");
            }
        } catch (Exception ex) {
            log.warn("No se pudo determinar clientStartEnabled automaticamente", ex);
        }

        return false;
    }

    private String encontrarPrimerNodoInteractivoDesdeStart(Document document) {
        NodeList startEvents = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "startEvent");
        if (startEvents.getLength() == 0) {
            return null;
        }

        Element startEvent = (Element) startEvents.item(0);
        String startEventId = startEvent.getAttribute("id");
        if (startEventId == null || startEventId.isBlank()) {
            return null;
        }

        String currentNodeId = encontrarSiguienteNodoDesde(document, startEventId.trim());
        int safetyCounter = 0;
        while (currentNodeId != null && safetyCounter < 25) {
            Element currentElement = encontrarElementoPorId(document, currentNodeId.trim());
            if (currentElement == null) {
                return currentNodeId.trim();
            }

            String localName = currentElement.getLocalName();
            if (localName != null && esNodoTarea(localName)) {
                return currentNodeId.trim();
            }

            if ("exclusiveGateway".equals(localName) || "parallelGateway".equals(localName) || "inclusiveGateway".equals(localName)) {
                currentNodeId = encontrarSiguienteNodoDesde(document, currentNodeId.trim());
                safetyCounter++;
                continue;
            }

            currentNodeId = encontrarSiguienteNodoDesde(document, currentNodeId.trim());
            safetyCounter++;
        }

        return null;
    }

    private String encontrarSiguienteNodoDesde(Document document, String sourceNodeId) {
        NodeList sequenceFlows = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element flow = (Element) sequenceFlows.item(i);
            String sourceRef = flow.getAttribute("sourceRef");
            if (sourceNodeId.equals(sourceRef != null ? sourceRef.trim() : null)) {
                String targetRef = flow.getAttribute("targetRef");
                if (targetRef != null && !targetRef.isBlank()) {
                    return targetRef.trim();
                }
            }
        }
        return null;
    }

    private Element encontrarElementoPorId(Document document, String elementId) {
        NodeList allNodes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element element = (Element) allNodes.item(i);
            if (elementId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private boolean esNodoTarea(String localName) {
        return "task".equals(localName)
                || "userTask".equals(localName)
                || localName.endsWith("Task");
    }

    private boolean laneContieneNodo(Element laneElement, String nodeId) {
        NodeList flowNodeRefs = laneElement.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "flowNodeRef");
        for (int i = 0; i < flowNodeRefs.getLength(); i++) {
            String ref = flowNodeRefs.item(i).getTextContent();
            if (nodeId.equals(ref != null ? ref.trim() : null)) {
                return true;
            }
        }
        return false;
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
