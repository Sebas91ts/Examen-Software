package com.systembpm.system.modules.processmonitoring.application.service;

import com.systembpm.system.modules.processmonitoring.application.dto.ProcessMonitoringResponseDto;

public interface IProcessMonitoringService {

    ProcessMonitoringResponseDto obtenerMonitoreo(String processId);
}
