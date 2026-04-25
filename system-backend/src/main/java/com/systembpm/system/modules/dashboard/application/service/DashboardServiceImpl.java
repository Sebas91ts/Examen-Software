package com.systembpm.system.modules.dashboard.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.dashboard.application.dto.DashboardMetricItemDto;
import com.systembpm.system.modules.dashboard.application.dto.DashboardRecentTaskDto;
import com.systembpm.system.modules.dashboard.application.dto.DashboardSummaryResponseDto;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.processinstance.infrastructure.repository.ProcesoInstanciaRepository;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String ESTADO_ACTIVA = "ACTIVA";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final int LIMITE_ULTIMOS_LOGS = 10;
    private static final long CAMUNDA_TIMEOUT_SECONDS = 4L;

    private final ProcesoRepository procesoRepository;
    private final ProcesoInstanciaRepository procesoInstanciaRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TareaInstanciaRepository tareaInstanciaRepository;
    private final CamundaService camundaService;

    @Override
    public DashboardSummaryResponseDto obtenerResumen() {
        log.info("Construyendo resumen del dashboard");
        List<Proceso> procesos = procesoRepository.findAll();
        long totalProcesosPublicados = procesos.stream()
                .map(Proceso::getEstado)
                .filter(Objects::nonNull)
                .filter(estado -> ESTADO_PUBLICADO.equalsIgnoreCase(estado))
                .count();

        long totalInstanciasActivas = procesoInstanciaRepository.findAll().stream()
                .map(instancia -> instancia.getEstado())
                .filter(Objects::nonNull)
                .filter(estado -> ESTADO_ACTIVA.equalsIgnoreCase(estado))
                .count();

        long totalTareasPendientes = obtenerTotalTareasPendientes();

        List<TaskExecutionLog> logs = taskExecutionLogRepository.findAll(Sort.by(
                Sort.Order.desc("completedAt"),
                Sort.Order.desc("createdAt")));

        DashboardSummaryResponseDto response = DashboardSummaryResponseDto.builder()
                .totalProcesosPublicados(totalProcesosPublicados)
                .totalInstanciasActivas(totalInstanciasActivas)
                .totalTareasPendientes(totalTareasPendientes)
                .totalTareasCompletadas(logs.size())
                .tareasCompletadasPorArea(groupByArea(logs))
                .tareasCompletadasPorUsuario(groupByUsuario(logs))
                .ultimosLogsTareas(mapUltimosLogs(logs))
                .build();

        log.info(
                "Resumen dashboard listo: procesosPublicados={}, instanciasActivas={}, tareasPendientes={}, tareasCompletadas={}, ultimosLogs={}",
                response.getTotalProcesosPublicados(),
                response.getTotalInstanciasActivas(),
                response.getTotalTareasPendientes(),
                response.getTotalTareasCompletadas(),
                response.getUltimosLogsTareas() != null ? response.getUltimosLogsTareas().size() : 0);

        return response;
    }

    private long obtenerTotalTareasPendientes() {
        try {
            return CompletableFuture
                    .supplyAsync(() -> (long) camundaService.listarTareasTodas().size())
                    .orTimeout(CAMUNDA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("No se pudo consultar Camunda para totalTareasPendientes. Se usara fallback local.", ex);
                        return tareaInstanciaRepository.countByEstadoIgnoreCase(ESTADO_PENDIENTE);
                    })
                    .join();
        } catch (Exception ex) {
            log.warn("No se pudo obtener totalTareasPendientes ni desde Camunda ni desde fallback local", ex);
            return tareaInstanciaRepository.countByEstadoIgnoreCase(ESTADO_PENDIENTE);
        }
    }

    private List<DashboardMetricItemDto> groupByArea(List<TaskExecutionLog> logs) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (TaskExecutionLog logEntry : logs) {
            String key = cleanLabel(logEntry.getAreaNombre(), "Area no identificada");
            totals.merge(key, 1L, Long::sum);
        }

        return totals.entrySet().stream()
                .map(entry -> DashboardMetricItemDto.builder()
                        .label(entry.getKey())
                        .total(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(DashboardMetricItemDto::getTotal).reversed()
                        .thenComparing(DashboardMetricItemDto::getLabel))
                .toList();
    }

    private List<DashboardMetricItemDto> groupByUsuario(List<TaskExecutionLog> logs) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (TaskExecutionLog logEntry : logs) {
            String key = cleanLabel(logEntry.getCompletedBy(), "Usuario no identificado");
            totals.merge(key, 1L, Long::sum);
        }

        return totals.entrySet().stream()
                .map(entry -> DashboardMetricItemDto.builder()
                        .label(entry.getKey())
                        .total(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(DashboardMetricItemDto::getTotal).reversed()
                        .thenComparing(DashboardMetricItemDto::getLabel))
                .toList();
    }

    private List<DashboardRecentTaskDto> mapUltimosLogs(List<TaskExecutionLog> logs) {
        return logs.stream()
                .limit(LIMITE_ULTIMOS_LOGS)
                .map(item -> DashboardRecentTaskDto.builder()
                        .processInstanceId(item.getProcessInstanceId())
                        .processKey(item.getProcessKey())
                        .processVersion(item.getProcessVersion())
                        .taskName(cleanLabel(item.getTaskName(), "Tarea sin nombre"))
                        .areaNombre(cleanLabel(item.getAreaNombre(), "Area no identificada"))
                        .assignedTo(item.getAssignedTo())
                        .completedBy(item.getCompletedBy())
                        .createdAt(item.getCreatedAt())
                        .completedAt(item.getCompletedAt())
                        .build())
                .toList();
    }

    private String cleanLabel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
