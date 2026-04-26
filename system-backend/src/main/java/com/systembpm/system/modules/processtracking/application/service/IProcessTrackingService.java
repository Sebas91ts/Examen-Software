package com.systembpm.system.modules.processtracking.application.service;

import com.systembpm.system.modules.processtracking.application.dto.ProcessInstanceTrackingResponseDto;

public interface IProcessTrackingService {

    ProcessInstanceTrackingResponseDto obtenerTracking(String processInstanceId);
}
