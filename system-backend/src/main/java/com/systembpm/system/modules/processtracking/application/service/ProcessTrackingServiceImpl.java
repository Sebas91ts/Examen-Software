package com.systembpm.system.modules.processtracking.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.processtracking.application.dto.ActiveProcessTaskDto;
import com.systembpm.system.modules.processtracking.application.dto.ProcessInstanceTrackingResponseDto;
import com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto;
import com.systembpm.system.modules.taskexecutionlog.application.service.ITaskExecutionLogService;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTrackingServiceImpl implements IProcessTrackingService {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private final CamundaService camundaService;
    private final ITaskExecutionLogService taskExecutionLogService;
    private final TareaInstanciaRepository tareaInstanciaRepository;
    private final ProcesoRepository procesoRepository;

    @Override
    public ProcessInstanceTrackingResponseDto obtenerTracking(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio");
        }

        String normalizedProcessInstanceId = processInstanceId.trim();
        List<Map<String, Object>> activeTaskSnapshots = obtenerTareasActivas(normalizedProcessInstanceId);
        List<TaskExecutionLogResponseDto> history = taskExecutionLogService.listarPorInstancia(normalizedProcessInstanceId);
        List<TareaInstancia> localTasks = tareaInstanciaRepository.findByProcessInstanceIdOrderByCreatedAtAsc(normalizedProcessInstanceId);

        TrackingMetadata metadata = resolveMetadata(normalizedProcessInstanceId, activeTaskSnapshots, history, localTasks);
        if (metadata.processDefinitionId() == null
                && metadata.processKey() == null
                && activeTaskSnapshots.isEmpty()
                && history.isEmpty()
                && localTasks.isEmpty()) {
            throw new IllegalArgumentException("No se encontro informacion de tracking para la instancia solicitada");
        }

        Optional<Proceso> processDefinition = resolveProceso(metadata);
        String processName = processDefinition.map(Proceso::getNombre)
                .filter(this::hasText)
                .orElseGet(() -> fallbackProcessName(metadata, localTasks));
        String xmlBpmn = processDefinition.map(Proceso::getXml).filter(this::hasText).orElse("");

        List<ActiveProcessTaskDto> activeTasks = activeTaskSnapshots.stream()
                .map(this::mapActiveTask)
                .toList();

        List<String> activeTaskKeys = distinctKeys(activeTasks.stream()
                .map(ActiveProcessTaskDto::getTaskDefinitionKey)
                .toList());
        List<String> completedTaskKeys = distinctKeys(history.stream()
                .map(TaskExecutionLogResponseDto::getTaskDefinitionKey)
                .toList());
        List<String> pendingTaskKeys = resolvePendingTaskKeys(xmlBpmn, completedTaskKeys, activeTaskKeys);

        ActiveProcessTaskDto currentTask = activeTasks.isEmpty() ? null : activeTasks.get(0);

        return ProcessInstanceTrackingResponseDto.builder()
                .processInstanceId(normalizedProcessInstanceId)
                .processDefinitionId(metadata.processDefinitionId())
                .processKey(metadata.processKey())
                .processVersion(metadata.processVersion())
                .nombreProceso(processName)
                .estado(resolveEstado(activeTasks, history, localTasks))
                .currentTaskName(currentTask != null ? currentTask.getTaskName() : null)
                .currentAreaNombre(currentTask != null ? currentTask.getAreaNombre() : null)
                .currentAssignedTo(currentTask != null ? currentTask.getAssignedTo() : null)
                .activeTasks(activeTasks)
                .history(history)
                .xmlBpmn(xmlBpmn)
                .completedTaskKeys(completedTaskKeys)
                .activeTaskKeys(activeTaskKeys)
                .pendingTaskKeys(pendingTaskKeys)
                .build();
    }

    private List<Map<String, Object>> obtenerTareasActivas(String processInstanceId) {
        try {
            return camundaService.listarTareasTodas().stream()
                    .filter(task -> processInstanceId.equals(stringValue(task.get("processInstanceId"))))
                    .toList();
        } catch (Exception ex) {
            log.warn("No se pudieron consultar tareas activas en Camunda para processInstanceId={}", processInstanceId, ex);
            return List.of();
        }
    }

    private TrackingMetadata resolveMetadata(
            String processInstanceId,
            List<Map<String, Object>> activeTaskSnapshots,
            List<TaskExecutionLogResponseDto> history,
            List<TareaInstancia> localTasks) {
        String processDefinitionId = firstNonBlank(
                activeTaskSnapshots.stream().map(task -> stringValue(task.get("processDefinitionId"))).toList(),
                history.stream().map(TaskExecutionLogResponseDto::getProcessDefinitionId).toList(),
                localTasks.stream().map(TareaInstancia::getProcessDefinitionId).toList());

        String processKey = firstNonBlank(
                activeTaskSnapshots.stream().map(task -> stringValue(task.get("processKey"))).toList(),
                history.stream().map(TaskExecutionLogResponseDto::getProcessKey).toList(),
                listOfIfPresent(extractProcessKey(processDefinitionId)));

        Integer processVersion = firstNonNull(
                history.stream().map(TaskExecutionLogResponseDto::getProcessVersion).toList(),
                listOfIfPresent(extractProcessVersion(processDefinitionId)));

        return new TrackingMetadata(processInstanceId, processDefinitionId, processKey, processVersion);
    }

    private Optional<Proceso> resolveProceso(TrackingMetadata metadata) {
        if (hasText(metadata.processKey())) {
            List<Proceso> processVersions = procesoRepository.findByProcessKey(metadata.processKey().trim());
            if (metadata.processVersion() != null) {
                Optional<Proceso> exactMatch = processVersions.stream()
                        .filter(item -> Objects.equals(item.getVersion(), metadata.processVersion()))
                        .findFirst();
                if (exactMatch.isPresent()) {
                    return exactMatch;
                }
            }

            Optional<Proceso> latestMatch = processVersions.stream()
                    .max(java.util.Comparator.comparing(item -> item.getVersion() != null ? item.getVersion() : 0));
            if (latestMatch.isPresent()) {
                return latestMatch;
            }
        }

        if (hasText(metadata.processDefinitionId()) && !metadata.processDefinitionId().contains(":")) {
            return procesoRepository.findById(metadata.processDefinitionId().trim());
        }

        return Optional.empty();
    }

    private ActiveProcessTaskDto mapActiveTask(Map<String, Object> task) {
        return ActiveProcessTaskDto.builder()
                .id(stringValue(task.get("id")))
                .taskDefinitionKey(stringValue(task.get("taskDefinitionKey")))
                .taskName(resolveTaskName(task))
                .areaId(stringValue(task.get("areaId")))
                .areaNombre(stringValue(task.get("areaNombre")))
                .assignedTo(firstNonBlankValue(
                        stringValue(task.get("assignee")),
                        stringValue(task.get("assignedTo"))))
                .createdAt(firstNonBlankValue(
                        stringValue(task.get("created")),
                        stringValue(task.get("createdAt"))))
                .build();
    }

    private String fallbackProcessName(TrackingMetadata metadata, List<TareaInstancia> localTasks) {
        String localName = firstNonBlank(localTasks.stream().map(TareaInstancia::getNombreProceso).toList());
        if (hasText(localName)) {
            return localName;
        }
        if (hasText(metadata.processKey())) {
            return metadata.processKey().trim().replace('_', ' ');
        }
        return "Proceso no identificado";
    }

    private String resolveEstado(
            List<ActiveProcessTaskDto> activeTasks,
            List<TaskExecutionLogResponseDto> history,
            List<TareaInstancia> localTasks) {
        if (!activeTasks.isEmpty()) {
            return "ACTIVA";
        }

        boolean hasPendingLocalTasks = localTasks.stream()
                .anyMatch(task -> hasText(task.getEstado()) && "PENDIENTE".equalsIgnoreCase(task.getEstado()));
        if (hasPendingLocalTasks) {
            return "ACTIVA";
        }

        if (!history.isEmpty()) {
            return "FINALIZADA";
        }

        return "SIN_DATOS";
    }

    private List<String> resolvePendingTaskKeys(String xmlBpmn, List<String> completedTaskKeys, List<String> activeTaskKeys) {
        if (!hasText(xmlBpmn)) {
            return List.of();
        }

        Set<String> excluded = new LinkedHashSet<>();
        excluded.addAll(completedTaskKeys);
        excluded.addAll(activeTaskKeys);

        List<String> orderedTaskKeys = extractTaskKeysFromBpmn(xmlBpmn);
        return orderedTaskKeys.stream()
                .filter(this::hasText)
                .filter(taskKey -> !excluded.contains(taskKey))
                .toList();
    }

    private List<String> extractTaskKeysFromBpmn(String xmlBpmn) {
        try {
            Document document = parseDocument(xmlBpmn);
            NodeList nodes = document.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
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

            return distinctKeys(taskKeys);
        } catch (Exception ex) {
            log.warn("No se pudo extraer la secuencia de tareas desde el BPMN del tracking", ex);
            return List.of();
        }
    }

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
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

    private String resolveTaskName(Map<String, Object> task) {
        return firstNonBlankValue(
                stringValue(task.get("name")),
                stringValue(task.get("nombreTarea")),
                stringValue(task.get("taskDefinitionKey")),
                "Tarea sin nombre");
    }

    private List<String> distinctKeys(List<String> values) {
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    @SafeVarargs
    private final String firstNonBlank(List<String>... sources) {
        for (List<String> source : sources) {
            for (String value : source) {
                if (hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(List<T>... sources) {
        for (List<T> source : sources) {
            for (T value : source) {
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private <T> List<T> listOfIfPresent(T value) {
        return value == null ? List.of() : List.of(value);
    }

    private String firstNonBlankValue(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String extractProcessKey(String processDefinitionId) {
        if (!hasText(processDefinitionId)) {
            return null;
        }

        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0].trim() : processDefinitionId.trim();
    }

    private Integer extractProcessVersion(String processDefinitionId) {
        if (!hasText(processDefinitionId)) {
            return null;
        }

        String[] parts = processDefinitionId.split(":");
        if (parts.length < 2) {
            return null;
        }

        try {
            return Integer.valueOf(parts[1].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record TrackingMetadata(
            String processInstanceId,
            String processDefinitionId,
            String processKey,
            Integer processVersion) {
    }
}
