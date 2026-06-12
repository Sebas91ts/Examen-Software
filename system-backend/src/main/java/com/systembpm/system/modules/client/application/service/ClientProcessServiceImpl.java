package com.systembpm.system.modules.client.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.client.application.dto.ClientProcessTrackingResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartPreviewDto;
import com.systembpm.system.modules.client.application.dto.ClientTrackingHistoryItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessInstanceListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartResponseDto;
import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import com.systembpm.system.modules.form.application.service.IFormDefinitionService;
import com.systembpm.system.modules.client.domain.ClientProcessInstance;
import com.systembpm.system.modules.client.infrastructure.repository.ClientProcessInstanceRepository;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.notification.application.service.INotificationService;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.taskexecutionlog.application.service.ITaskExecutionLogService;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientProcessServiceImpl implements ClientProcessService {

    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String ESTADO_ACTIVA = "ACTIVA";
    private static final int FIRST_TASK_LOOKUP_ATTEMPTS = 8;
    private static final long FIRST_TASK_LOOKUP_DELAY_MS = 250;
    private static final Set<String> CLIENT_HISTORY_EXCLUDED_KEYS = Set.of(
            "clientUserId",
            "clientEmail",
            "startedByRole"
    );

    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClientProcessInstanceRepository clientProcessInstanceRepository;
    private final CamundaService camundaService;
    private final IFormDefinitionService formDefinitionService;
    private final ITaskExecutionLogService taskExecutionLogService;
    private final INotificationService notificationService;
    private final IRealtimeEventService realtimeEventService;

    @Override
    public List<ClientProcessListItemDto> listarProcesosDisponibles() {
        return procesoRepository.findByEstadoIgnoreCase(ESTADO_PUBLICADO).stream()
                .filter(this::puedeIniciarCliente)
                .map(proceso -> ClientProcessListItemDto.builder()
                        .processId(proceso.getId())
                        .processKey(proceso.getProcessKey())
                        .nombre(proceso.getNombre())
                        .version(proceso.getVersion())
                        .descripcion(proceso.getDescripcion())
                        .build())
                .toList();
    }

    @Override
    public ClientProcessStartPreviewDto obtenerVistaInicio(String processId) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("El proceso es obligatorio");
        }

        Proceso proceso = procesoRepository.findById(processId.trim())
                .orElseThrow(() -> new IllegalArgumentException("El proceso no existe"));

        validarProcesoHabilitado(proceso);

        PrimerPasoCliente primerPaso = extraerPrimerPasoCliente(proceso.getXml());
        FormDefinitionResponseDto formDefinition = null;
        if (primerPaso.taskDefinitionKey() != null && !primerPaso.taskDefinitionKey().isBlank()) {
            formDefinition = formDefinitionService
                    .obtenerPorClave(proceso.getProcessKey(), proceso.getVersion(), primerPaso.taskDefinitionKey())
                    .orElse(null);
        }

        return ClientProcessStartPreviewDto.builder()
                .processId(proceso.getId())
                .processKey(proceso.getProcessKey())
                .processVersion(proceso.getVersion())
                .processName(proceso.getNombre())
                .firstTaskDefinitionKey(primerPaso.taskDefinitionKey())
                .firstTaskName(primerPaso.taskName())
                .firstTaskAreaId(primerPaso.areaId())
                .firstTaskAreaName(primerPaso.areaName())
                .formDefinition(formDefinition)
                .build();
    }

    @Override
    public ClientProcessStartResponseDto iniciarTramite(String processId, String clientEmail, Map<String, Object> variables) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("El proceso es obligatorio");
        }
        if (clientEmail == null || clientEmail.isBlank()) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }

        Usuario cliente = usuarioRepository.findByEmail(clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario cliente autenticado"));

        log.info("Iniciando tramite cliente: processId={} clientEmail={}", processId, clientEmail);

        Proceso proceso = procesoRepository.findById(processId.trim())
                .orElseThrow(() -> new IllegalArgumentException("El proceso no existe"));

        validarProcesoHabilitado(proceso);

        PrimerPasoCliente primerPaso = extraerPrimerPasoCliente(proceso.getXml());
        Map<String, Object> normalizedVariables = normalizarVariables(variables);
        normalizedVariables.putIfAbsent("clientUserId", cliente.getId());
        normalizedVariables.putIfAbsent("clientEmail", cliente.getEmail());
        normalizedVariables.putIfAbsent("startedByRole", "CLIENT");

        log.debug("Variables normalizadas para inicio cliente processId={}: {}", processId, normalizedVariables.keySet());
        Map<String, Object> camundaResponse = iniciarInstanciaClienteConRecuperacion(proceso, normalizedVariables);
        String processInstanceId = stringValue(camundaResponse.get("processInstanceId"));
        if (processInstanceId == null || processInstanceId.isBlank()) {
            processInstanceId = stringValue(camundaResponse.get("id"));
        }
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("Camunda no devolvio el processInstanceId");
        }

        ClientProcessInstance instance = ClientProcessInstance.builder()
                .clientUserId(cliente.getId())
                .clientEmail(cliente.getEmail())
                .processId(proceso.getId())
                .processKey(proceso.getProcessKey())
                .processVersion(proceso.getVersion())
                .processName(proceso.getNombre())
                .processInstanceId(processInstanceId)
                .estado(ESTADO_ACTIVA)
                .startedAt(LocalDateTime.now())
                .finishedAt(null)
                .build();

        ClientProcessInstance saved = clientProcessInstanceRepository.save(instance);
        notificationService.notifyUserByEmail(
                cliente.getEmail(),
                "Tu tramite fue iniciado",
                "El tramite \"" + proceso.getNombre() + "\" se inicio correctamente y ya puedes revisar su seguimiento.",
                "PROCESS_STARTED",
                processInstanceId,
                null);

        completarPrimeraTareaCliente(processInstanceId, normalizedVariables, cliente);

        try {
            log.debug("Publicando tareas activas iniciales del tramite cliente processInstanceId={}", processInstanceId);
            publicarEventosTareasActivas(processInstanceId);
        } catch (Exception ex) {
            log.warn("No se pudieron publicar las tareas activas iniciales del tramite cliente {}", processInstanceId, ex);
        }
        log.info("Tramite de cliente iniciado: processId={} processInstanceId={} user={} firstTask={}",
                proceso.getId(), processInstanceId, cliente.getEmail(), primerPaso.taskDefinitionKey());

        return ClientProcessStartResponseDto.builder()
                .id(saved.getId())
                .clientUserId(saved.getClientUserId())
                .clientEmail(saved.getClientEmail())
                .processId(saved.getProcessId())
                .processKey(saved.getProcessKey())
                .processVersion(saved.getProcessVersion())
                .processName(saved.getProcessName())
                .processInstanceId(saved.getProcessInstanceId())
                .estado(saved.getEstado())
                .startedAt(saved.getStartedAt())
                .finishedAt(saved.getFinishedAt())
                .build();
    }

    @Override
    public List<ClientProcessInstanceListItemDto> listarMisInstancias(String clientEmail) {
        if (clientEmail == null || clientEmail.isBlank()) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }

        Usuario cliente = usuarioRepository.findByEmail(clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario cliente autenticado"));

        return clientProcessInstanceRepository.findByClientUserIdOrderByStartedAtDesc(cliente.getId()).stream()
                .map(instance -> ClientProcessInstanceListItemDto.builder()
                        .id(instance.getId())
                        .processName(instance.getProcessName())
                        .processKey(instance.getProcessKey())
                        .processVersion(instance.getProcessVersion())
                        .processInstanceId(instance.getProcessInstanceId())
                        .estado(instance.getEstado())
                        .startedAt(instance.getStartedAt())
                        .finishedAt(instance.getFinishedAt())
                        .build())
                .toList();
    }

    @Override
    public ClientProcessTrackingResponseDto obtenerTrackingCliente(String processInstanceId, String clientEmail) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("La instancia es obligatoria");
        }
        if (clientEmail == null || clientEmail.isBlank()) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }

        Usuario cliente = usuarioRepository.findByEmail(clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario cliente autenticado"));

        log.info("Obteniendo tracking cliente: processInstanceId={} clientEmail={}", processInstanceId, clientEmail);

        ClientProcessInstance clientInstance = clientProcessInstanceRepository
                .findByProcessInstanceIdAndClientEmail(processInstanceId.trim(), clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a esta instancia"));

        ClientProcessTrackingResponseDto baseTracking;
        try {
            log.debug("Construyendo tracking cliente seguro para processInstanceId={}", processInstanceId);
            baseTracking = buildClientTrackingSeguro(processInstanceId.trim(), clientInstance, clientEmail.trim());
        } catch (Exception ex) {
            log.warn("No se pudo construir el tracking cliente completo para processInstanceId={} clientEmail={}",
                    processInstanceId, clientEmail, ex);
            baseTracking = buildFallbackClientTracking(processInstanceId.trim(), clientInstance);
        }

        return ClientProcessTrackingResponseDto.builder()
                .processName(clientInstance.getProcessName())
                .estado(baseTracking.getEstado())
                .startedAt(clientInstance.getStartedAt())
                .progressPercentage(baseTracking.getProgressPercentage())
                .currentTaskName(safeText(baseTracking.getCurrentTaskName()))
                .currentAreaName(safeText(baseTracking.getCurrentAreaName()))
                .history(baseTracking.getHistory())
                .xmlBpmn(baseTracking.getXmlBpmn())
                .completedTaskKeys(baseTracking.getCompletedTaskKeys())
                .activeTaskKeys(baseTracking.getActiveTaskKeys())
                .pendingTaskKeys(baseTracking.getPendingTaskKeys())
                .build();
    }

    private ClientProcessTrackingResponseDto buildFallbackClientTracking(String processInstanceId, ClientProcessInstance clientInstance) {
        Proceso proceso = findProcesoCliente(clientInstance);
        String xmlBpmn = proceso != null ? safeXml(proceso.getXml()) : "";
        return ClientProcessTrackingResponseDto.builder()
                .estado(clientInstance != null && hasText(clientInstance.getEstado()) ? clientInstance.getEstado() : "ACTIVA")
                .progressPercentage(0)
                .currentTaskName(null)
                .currentAreaName(null)
                .history(List.of())
                .xmlBpmn(xmlBpmn)
                .completedTaskKeys(List.of())
                .activeTaskKeys(List.of())
                .pendingTaskKeys(resolvePendingTaskKeysFromXml(xmlBpmn, List.of(), List.of()))
                .build();
    }

    private ClientProcessTrackingResponseDto buildClientTrackingSeguro(String processInstanceId, ClientProcessInstance clientInstance, String clientEmail) {
        log.debug("buildClientTrackingSeguro: consultando historial de ejecucion para processInstanceId={}", processInstanceId);
        List<com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto> historyLogs;
        try {
            historyLogs = taskExecutionLogService.listarPorInstancia(processInstanceId);
        } catch (Throwable ex) {
            log.error("buildClientTrackingSeguro: fallo al consultar historial para processInstanceId={}", processInstanceId, ex);
            historyLogs = List.of();
        }
        if (historyLogs == null) {
            historyLogs = List.of();
        }
        historyLogs = historyLogs.stream()
                .filter(entry -> entry != null
                        && hasText(entry.getCompletedBy())
                        && hasText(clientEmail)
                        && entry.getCompletedBy().trim().equalsIgnoreCase(clientEmail.trim())
                        && isClientHistoryArea(entry.getAreaNombre()))
                .toList();
        log.debug("buildClientTrackingSeguro: logs de historial encontrados={}", historyLogs.size());

        List<ClientTrackingHistoryItemDto> history = historyLogs.stream()
                .map(entry -> ClientTrackingHistoryItemDto.builder()
                        .taskName(entry.getTaskName())
                        .areaName(entry.getAreaNombre())
                        .completedAt(entry.getCompletedAt())
                        .formData(sanitizeFormData(entry.getFormData()))
                        .build())
                .toList();

        String lastTaskDefinitionKey = historyLogs.isEmpty()
                ? null
                : historyLogs.get(historyLogs.size() - 1).getTaskDefinitionKey();
        List<String> activeKeys = hasText(lastTaskDefinitionKey)
                ? List.of(lastTaskDefinitionKey)
                : List.of();

        List<String> completedKeys = historyLogs.stream()
                .map(com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto::getTaskDefinitionKey)
                .filter(this::hasText)
                .distinct()
                .toList();

        Proceso proceso;
        try {
            proceso = findProcesoCliente(clientInstance);
        } catch (Throwable ex) {
            log.error("buildClientTrackingSeguro: fallo resolviendo proceso para processInstanceId={}", processInstanceId, ex);
            proceso = null;
        }
        log.debug("buildClientTrackingSeguro: proceso resuelto={} para processInstanceId={}",
                proceso != null ? proceso.getProcessKey() : "null", processInstanceId);
        String xmlBpmn = proceso != null ? safeXml(proceso.getXml()) : "";
        List<String> pendingKeys;
        try {
            pendingKeys = resolvePendingTaskKeysFromXml(xmlBpmn, completedKeys, activeKeys);
        } catch (Throwable ex) {
            log.error("buildClientTrackingSeguro: fallo calculando tareas pendientes para processInstanceId={}", processInstanceId, ex);
            pendingKeys = List.of();
        }
        log.debug("buildClientTrackingSeguro: keys completed={} active={} pending={}",
                completedKeys.size(), activeKeys.size(), pendingKeys.size());

        ClientTrackingHistoryItemDto lastHistory = history.isEmpty() ? null : history.get(history.size() - 1);

        return ClientProcessTrackingResponseDto.builder()
                .estado(pendingKeys.isEmpty() && !history.isEmpty() ? "FINALIZADA" : "ACTIVA")
                .progressPercentage(calcularProgreso(completedKeys, activeKeys, pendingKeys))
                .currentTaskName(lastHistory != null ? lastHistory.getTaskName() : null)
                .currentAreaName(lastHistory != null ? lastHistory.getAreaName() : null)
                .history(history)
                .xmlBpmn(xmlBpmn)
                .completedTaskKeys(completedKeys)
                .activeTaskKeys(activeKeys)
                .pendingTaskKeys(pendingKeys)
                .build();
    }

    private void validarProcesoHabilitado(Proceso proceso) {
        if (proceso == null) {
            throw new IllegalArgumentException("El proceso no existe");
        }

        if (proceso.getEstado() == null || !ESTADO_PUBLICADO.equalsIgnoreCase(proceso.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden iniciar tramites desde procesos PUBLICADOS");
        }

        if (!puedeIniciarCliente(proceso)) {
            throw new IllegalArgumentException("Este proceso no esta habilitado para clientes");
        }
    }

    private boolean puedeIniciarCliente(Proceso proceso) {
        if (proceso == null) {
            return false;
        }

        if (proceso.isClientStartEnabled()) {
            return true;
        }

        return detectarPrimerPasoCliente(proceso.getXml());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void publicarEventosTareasActivas(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            log.warn("No se pudo publicar eventos activos porque el processInstanceId es invalido");
            return;
        }

        List<Map<String, Object>> tareasBase = camundaService.listarTareasTodas();
        if (tareasBase == null) {
            tareasBase = List.of();
        }

        List<Map<String, Object>> tareasIniciales = tareasBase.stream()
                .filter(tarea -> processInstanceId.equals(String.valueOf(tarea.get("processInstanceId"))))
                .toList();

        log.info("Se encontraron {} tareas activas para processInstanceId={}", tareasIniciales.size(), processInstanceId);

        for (Map<String, Object> tarea : tareasIniciales) {
            String areaId = stringValue(tarea.get("areaId"));
            String areaNombre = stringValue(tarea.get("areaNombre"));
            String taskId = stringValue(tarea.get("id"));
            String taskName = stringValue(tarea.get("name"));

            notificationService.notifyTaskAvailableForArea(areaId, areaNombre, processInstanceId, taskId, taskName);
            realtimeEventService.publishTaskCreated(tarea);
        }
    }

    private void completarPrimeraTareaCliente(String processInstanceId, Map<String, Object> variables, Usuario cliente) {
        log.debug("Buscando primera tarea de instancia cliente processInstanceId={}", processInstanceId);
        Map<String, Object> firstTaskSnapshot = encontrarPrimeraTareaDeInstanciaConReintentos(processInstanceId);
        if (firstTaskSnapshot.isEmpty()) {
            throw new IllegalStateException("La instancia fue creada, pero Camunda no expuso la primera tarea a tiempo");
        }

        String taskId = stringValue(firstTaskSnapshot.get("id"));
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("Camunda devolvio una primera tarea sin identificador");
        }

        log.info("Completando primera tarea cliente processInstanceId={} taskId={} taskKey={} user={}",
                processInstanceId, taskId, stringValue(firstTaskSnapshot.get("taskDefinitionKey")), cliente.getEmail());

        camundaService.completarTarea(taskId, variables, cliente.getEmail());
        taskExecutionLogService.registrarEjecucion(firstTaskSnapshot, variables, cliente.getEmail());
        notificationService.notifyTaskCompleted(firstTaskSnapshot, cliente.getEmail());
        realtimeEventService.publishTaskCompleted(firstTaskSnapshot, cliente.getEmail());
    }

    private Map<String, Object> encontrarPrimeraTareaDeInstanciaConReintentos(String processInstanceId) {
        for (int attempt = 1; attempt <= FIRST_TASK_LOOKUP_ATTEMPTS; attempt++) {
            Map<String, Object> task = encontrarPrimeraTareaDeInstancia(processInstanceId);
            if (!task.isEmpty()) {
                if (attempt > 1) {
                    log.info("Primera tarea cliente encontrada tras reintentos processInstanceId={} attempts={}",
                            processInstanceId, attempt);
                }
                return task;
            }

            esperarPrimeraTarea(processInstanceId, attempt);
        }

        return Map.of();
    }

    private void esperarPrimeraTarea(String processInstanceId, int attempt) {
        try {
            Thread.sleep(FIRST_TASK_LOOKUP_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio la espera de la primera tarea cliente", ex);
        }
        log.debug("Primera tarea cliente aun no disponible processInstanceId={} attempt={}/{}",
                processInstanceId, attempt, FIRST_TASK_LOOKUP_ATTEMPTS);
    }

    private Map<String, Object> encontrarPrimeraTareaDeInstancia(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Map.of();
        }

        List<Map<String, Object>> allTasks = camundaService.listarTareasTodas();
        if (allTasks == null) {
            return Map.of();
        }

        return allTasks.stream()
                .filter(tarea -> processInstanceId.equals(String.valueOf(tarea.get("processInstanceId"))))
                .findFirst()
                .orElse(Map.of());
    }

    private PrimerPasoCliente extraerPrimerPasoCliente(String xml) {
        if (xml == null || xml.isBlank()) {
            return new PrimerPasoCliente(null, null, null, null);
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
                return new PrimerPasoCliente(null, null, null, null);
            }

            Element firstNode = encontrarElementoPorId(document, firstInteractiveNodeId.trim());
            if (firstNode == null) {
                return new PrimerPasoCliente(firstInteractiveNodeId.trim(), null, null, null);
            }

            String taskName = firstNode.getAttribute("name");
            if (taskName == null || taskName.isBlank()) {
                taskName = firstInteractiveNodeId.trim();
            }

            String laneName = null;
            String areaId = null;
            NodeList lanes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "lane");
            for (int i = 0; i < lanes.getLength(); i++) {
                Element lane = (Element) lanes.item(i);
                if (!laneContieneNodo(lane, firstInteractiveNodeId.trim())) {
                    continue;
                }

                laneName = lane.getAttribute("name");
                areaId = leerAreaIdDeLane(lane);
                break;
            }

            return new PrimerPasoCliente(firstInteractiveNodeId.trim(), taskName.trim(), areaId, laneName);
        } catch (Exception ex) {
            log.warn("No se pudo extraer el primer paso del proceso para cliente", ex);
            return new PrimerPasoCliente(null, null, null, null);
        }
    }

    private String leerAreaIdDeLane(Element laneElement) {
        NodeList areaRefs = laneElement.getElementsByTagNameNS("http://systembpm.com/schema", "areaRef");
        if (areaRefs.getLength() == 0) {
            return null;
        }

        String value = areaRefs.item(0).getTextContent();
        return value != null ? value.trim() : null;
    }

    private Map<String, Object> normalizarVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String key = entry.getKey().trim();
            if (key.isBlank()) {
                continue;
            }

            normalized.put(key, entry.getValue());
        }

        return normalized;
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

            Element firstNode = encontrarElementoPorId(document, firstInteractiveNodeId.trim());
            if (firstNode == null) {
                return false;
            }

            String localName = firstNode.getLocalName();
            if (localName == null || !esNodoInteractivo(localName)) {
                return false;
            }

            NodeList lanes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "lane");
            for (int i = 0; i < lanes.getLength(); i++) {
                Element lane = (Element) lanes.item(i);
                if (!laneContieneNodo(lane, firstInteractiveNodeId.trim())) {
                    continue;
                }

                String laneName = lane.getAttribute("name");
                return laneName != null && laneName.trim().equalsIgnoreCase("Cliente");
            }
        } catch (Exception ex) {
            log.warn("No se pudo determinar si el proceso puede ser iniciado por cliente", ex);
        }

        return false;
    }

    private Map<String, Object> iniciarInstanciaClienteConRecuperacion(Proceso proceso, Map<String, Object> normalizedVariables) {
        try {
            return camundaService.iniciarInstanciaConVariables(proceso.getProcessKey(), normalizedVariables);
        } catch (IllegalArgumentException ex) {
            if (!debeReintentarDespliegue(ex)) {
                throw ex;
            }

            log.warn("No se encontro la definicion en Camunda para processKey={}. Se intentara redeploy automatico del proceso publicado {} y un nuevo intento de inicio.",
                    proceso.getProcessKey(), proceso.getId());

            camundaService.desplegarProceso(proceso.getId());
            return camundaService.iniciarInstanciaConVariables(proceso.getProcessKey(), normalizedVariables);
        }
    }

    private boolean debeReintentarDespliegue(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase();
        return normalized.contains("no matching process definition")
                || normalized.contains("no se encontro la definicion")
                || normalized.contains("no matching process definition with key");
    }

    private int calcularProgreso(List<String> completedTaskKeys, List<String> activeTaskKeys, List<String> pendingTaskKeys) {
        int completed = completedTaskKeys != null ? (int) completedTaskKeys.stream().filter(this::hasText).distinct().count() : 0;
        int active = activeTaskKeys != null ? (int) activeTaskKeys.stream().filter(this::hasText).distinct().count() : 0;
        int pending = pendingTaskKeys != null ? (int) pendingTaskKeys.stream().filter(this::hasText).distinct().count() : 0;
        int total = completed + active + pending;
        if (total <= 0) {
            return 0;
        }

        return Math.min(100, Math.max(0, Math.round((completed * 100.0f) / total)));
    }

    private String safeXml(String xml) {
        return hasText(xml) ? xml : "";
    }

    private Proceso findProcesoCliente(ClientProcessInstance clientInstance) {
        if (clientInstance == null || !hasText(clientInstance.getProcessKey())) {
            return null;
        }

        List<Proceso> procesos = procesoRepository.findByProcessKey(clientInstance.getProcessKey());
        if (procesos == null || procesos.isEmpty()) {
            return null;
        }

        if (clientInstance.getProcessVersion() != null) {
            for (Proceso proceso : procesos) {
                if (Objects.equals(proceso.getVersion(), clientInstance.getProcessVersion())) {
                    return proceso;
                }
            }
        }

        return procesos.stream()
                .max(java.util.Comparator.comparing(proceso -> proceso.getVersion() != null ? proceso.getVersion() : 0))
                .orElse(null);
    }

    private List<String> resolvePendingTaskKeysFromXml(String xmlBpmn, List<String> completedTaskKeys, List<String> activeTaskKeys) {
        if (!hasText(xmlBpmn)) {
            return List.of();
        }

        List<String> orderedTaskKeys = extractTaskKeysFromBpmn(xmlBpmn);
        List<String> excluded = new ArrayList<>();
        excluded.addAll(completedTaskKeys != null ? completedTaskKeys : List.of());
        excluded.addAll(activeTaskKeys != null ? activeTaskKeys : List.of());

        return orderedTaskKeys.stream()
                .filter(this::hasText)
                .filter(taskKey -> excluded.stream().noneMatch(excludedKey -> excludedKey.equalsIgnoreCase(taskKey)))
                .distinct()
                .toList();
    }

    private List<String> extractTaskKeysFromBpmn(String xmlBpmn) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlBpmn)));
            NodeList nodes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "*");
            List<String> taskKeys = new ArrayList<>();

            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String localName = element.getLocalName();
                if (!isTrackableTask(localName)) {
                    continue;
                }

                String id = element.getAttribute("id");
                if (hasText(id)) {
                    taskKeys.add(id.trim());
                }
            }

            return taskKeys.stream()
                    .filter(this::hasText)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            log.warn("No se pudo extraer las tareas del BPMN para tracking cliente", ex);
            return List.of();
        }
    }

    private boolean isTrackableTask(String localName) {
        if (!hasText(localName)) {
            return false;
        }

        return "task".equals(localName)
                || "userTask".equals(localName)
                || "manualTask".equals(localName)
                || "serviceTask".equals(localName)
                || "scriptTask".equals(localName)
                || "businessRuleTask".equals(localName)
                || "sendTask".equals(localName)
                || "receiveTask".equals(localName);
    }

    private String safeText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeText(String value) {
        if (!hasText(value)) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "")
                .toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Map<String, Object> sanitizeFormData(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }

            if (CLIENT_HISTORY_EXCLUDED_KEYS.contains(entry.getKey().trim())) {
                continue;
            }

            Object value = sanitizeValue(entry.getValue());
            if (value != null) {
                sanitized.put(entry.getKey().trim(), value);
            }
        }

        return sanitized;
    }

    private boolean isClientHistoryArea(String areaName) {
        if (!hasText(areaName)) {
            return false;
        }

        String normalized = normalizeText(areaName);
        return normalized.equals("cliente")
                || normalized.startsWith("cliente ")
                || normalized.startsWith("cliente-")
                || normalized.equals("clienteexterno")
                || normalized.startsWith("cliente externo");
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String stringValue) {
            return stringValue;
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> fileLike = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nestedEntry : mapValue.entrySet()) {
                if (nestedEntry.getKey() == null) {
                    continue;
                }

                String key = String.valueOf(nestedEntry.getKey());
                if ("secureUrl".equalsIgnoreCase(key)
                        || "fileName".equalsIgnoreCase(key)
                        || "publicId".equalsIgnoreCase(key)
                        || "mimeType".equalsIgnoreCase(key)
                        || "resourceType".equalsIgnoreCase(key)
                        || "size".equalsIgnoreCase(key)) {
                    fileLike.put(key, nestedEntry.getValue());
                }
            }

            if (!fileLike.isEmpty()) {
                return fileLike;
            }

            return mapValue.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new));
        }

        if (value instanceof List<?> listValue) {
            List<Object> sanitizedItems = new ArrayList<>();
            for (Object item : listValue) {
                Object sanitizedItem = sanitizeValue(item);
                if (sanitizedItem != null) {
                    sanitizedItems.add(sanitizedItem);
                }
            }
            return sanitizedItems;
        }

        return String.valueOf(value);
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
            if (localName != null && esNodoInteractivo(localName)) {
                return currentNodeId.trim();
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

    private boolean esNodoInteractivo(String localName) {
        return "task".equals(localName)
                || "userTask".equals(localName)
                || "serviceTask".equals(localName)
                || "manualTask".equals(localName)
                || "scriptTask".equals(localName)
                || "businessRuleTask".equals(localName)
                || "receiveTask".equals(localName)
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

    private record PrimerPasoCliente(String taskDefinitionKey, String taskName, String areaId, String areaName) {
    }
}
