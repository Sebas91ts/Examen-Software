package com.systembpm.system.modules.dashboard.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponseDto {

    private long totalProcesosPublicados;
    private long totalInstanciasActivas;
    private long totalTareasPendientes;
    private long totalTareasCompletadas;
    private List<DashboardMetricItemDto> tareasPendientesPorArea;
    private List<DashboardMetricItemDto> tareasCompletadasPorArea;
    private List<DashboardMetricItemDto> tareasCompletadasPorUsuario;
    private String actividadConMasPendientes;
    private long actividadConMasPendientesTotal;
    private String tareaConMayorTiempoPromedioEspera;
    private long tareaConMayorTiempoPromedioEsperaMinutos;
    private String areaConMasAcumulacion;
    private long areaConMasAcumulacionTotal;
    private List<DashboardRecentTaskDto> ultimosLogsTareas;
}
