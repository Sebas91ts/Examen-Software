package com.systembpm.system.modules.dashboard.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.dashboard.application.dto.DashboardMetricItemDto;
import com.systembpm.system.modules.dashboard.application.dto.DashboardRecentTaskDto;
import com.systembpm.system.modules.dashboard.application.dto.DashboardSummaryResponseDto;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private static final long CAMUNDA_INSTANCES_TIMEOUT_SECONDS = 4L;

    private final ProcesoRepository procesoRepository;
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

        long totalInstanciasActivas = obtenerTotalInstanciasActivas();

        long totalTareasPendientes = obtenerTotalTareasPendientes();
        List<TareaInstancia> tareasPendientes = tareaInstanciaRepository
                .findByEstadoIgnoreCaseOrderByCreatedAtAsc(ESTADO_PENDIENTE);

        List<TaskExecutionLog> logs = taskExecutionLogRepository.findAll(Sort.by(
                Sort.Order.desc("completedAt"),
                Sort.Order.desc("createdAt")));

        List<DashboardMetricItemDto> tareasPendientesPorArea = groupPendingByArea(tareasPendientes);
        DashboardMetricItemDto actividadConMasPendientes = topPendingByTask(tareasPendientes);
        DashboardMetricItemDto areaConMasAcumulacion = topPendingByArea(tareasPendientesPorArea);
        TaskAverageWait tareaConMayorEspera = resolveTaskWithLongestAverageWait(logs);

        DashboardSummaryResponseDto response = DashboardSummaryResponseDto.builder()
                .totalProcesosPublicados(totalProcesosPublicados)
                .totalInstanciasActivas(totalInstanciasActivas)
                .totalTareasPendientes(totalTareasPendientes)
                .totalTareasCompletadas(logs.size())
                .tareasPendientesPorArea(tareasPendientesPorArea)
                .tareasCompletadasPorArea(groupByArea(logs))
                .tareasCompletadasPorUsuario(groupByUsuario(logs))
                .actividadConMasPendientes(actividadConMasPendientes.getLabel())
                .actividadConMasPendientesTotal(actividadConMasPendientes.getTotal())
                .tareaConMayorTiempoPromedioEspera(tareaConMayorEspera.label())
                .tareaConMayorTiempoPromedioEsperaMinutos(tareaConMayorEspera.averageMinutes())
                .areaConMasAcumulacion(areaConMasAcumulacion.getLabel())
                .areaConMasAcumulacionTotal(areaConMasAcumulacion.getTotal())
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

    private long obtenerTotalInstanciasActivas() {
        try {
            return CompletableFuture
                    .supplyAsync(() -> (long) camundaService.listarInstanciasProcesoActivas().size())
                    .orTimeout(CAMUNDA_INSTANCES_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("No se pudo consultar Camunda para totalInstanciasActivas. Se usara fallback local por logs.", ex);
                        return taskExecutionLogRepository.findAll().stream()
                                .map(TaskExecutionLog::getProcessInstanceId)
                                .filter(Objects::nonNull)
                                .filter(id -> !id.isBlank())
                                .distinct()
                                .count();
                    })
                    .join();
        } catch (Exception ex) {
            log.warn("No se pudo obtener totalInstanciasActivas desde Camunda ni desde fallback local", ex);
            return taskExecutionLogRepository.findAll().stream()
                    .map(TaskExecutionLog::getProcessInstanceId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .count();
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

    private List<DashboardMetricItemDto> groupPendingByArea(List<TareaInstancia> pendingTasks) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (TareaInstancia task : pendingTasks) {
            String key = cleanLabel(task.getAreaNombre(), "Area no identificada");
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

    private DashboardMetricItemDto topPendingByTask(List<TareaInstancia> pendingTasks) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (TareaInstancia task : pendingTasks) {
            String key = cleanLabel(task.getNombreTarea(), "Actividad no identificada");
            totals.merge(key, 1L, Long::sum);
        }

        return totals.entrySet().stream()
                .map(entry -> DashboardMetricItemDto.builder()
                        .label(entry.getKey())
                        .total(entry.getValue())
                        .build())
                .max(Comparator.comparing(DashboardMetricItemDto::getTotal)
                        .thenComparing(DashboardMetricItemDto::getLabel))
                .orElseGet(() -> DashboardMetricItemDto.builder()
                        .label("Sin tareas pendientes")
                        .total(0L)
                        .build());
    }

    private DashboardMetricItemDto topPendingByArea(List<DashboardMetricItemDto> pendingByArea) {
        return pendingByArea.stream()
                .max(Comparator.comparing(DashboardMetricItemDto::getTotal)
                        .thenComparing(DashboardMetricItemDto::getLabel))
                .orElseGet(() -> DashboardMetricItemDto.builder()
                        .label("Sin acumulacion")
                        .total(0L)
                        .build());
    }

    private TaskAverageWait resolveTaskWithLongestAverageWait(List<TaskExecutionLog> logs) {
        record WaitAccumulator(long totalMinutes, long samples) {
            WaitAccumulator add(long minutes) {
                return new WaitAccumulator(totalMinutes + minutes, samples + 1);
            }

            long averageMinutes() {
                return samples == 0 ? 0 : Math.round((double) totalMinutes / samples);
            }
        }

        Map<String, WaitAccumulator> totals = new LinkedHashMap<>();
        for (TaskExecutionLog logEntry : logs) {
            if (logEntry.getCreatedAt() == null || logEntry.getCompletedAt() == null) {
                continue;
            }

            long minutes = Math.max(0L, Duration.between(logEntry.getCreatedAt(), logEntry.getCompletedAt()).toMinutes());
            String key = cleanLabel(logEntry.getTaskName(), "Actividad no identificada");
            totals.compute(key, (ignoredKey, current) -> current == null ? new WaitAccumulator(minutes, 1) : current.add(minutes));
        }

        return totals.entrySet().stream()
                .map(entry -> new TaskAverageWait(entry.getKey(), entry.getValue().averageMinutes()))
                .max(Comparator.comparing(TaskAverageWait::averageMinutes)
                        .thenComparing(TaskAverageWait::label))
                .orElseGet(() -> new TaskAverageWait("Sin historial suficiente", 0L));
    }

    private String cleanLabel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record TaskAverageWait(String label, long averageMinutes) {
    }
}
