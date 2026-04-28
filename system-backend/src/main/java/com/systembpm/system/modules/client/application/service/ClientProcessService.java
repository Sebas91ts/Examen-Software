package com.systembpm.system.modules.client.application.service;

import com.systembpm.system.modules.client.application.dto.ClientProcessInstanceListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessListItemDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessTrackingResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartPreviewDto;
import com.systembpm.system.modules.client.application.dto.ClientProcessStartResponseDto;

import java.util.List;
import java.util.Map;

public interface ClientProcessService {
    List<ClientProcessListItemDto> listarProcesosDisponibles();

    ClientProcessStartPreviewDto obtenerVistaInicio(String processId);

    ClientProcessStartResponseDto iniciarTramite(String processId, String clientEmail, Map<String, Object> variables);

    List<ClientProcessInstanceListItemDto> listarMisInstancias(String clientEmail);

    ClientProcessTrackingResponseDto obtenerTrackingCliente(String processInstanceId, String clientEmail);
}
