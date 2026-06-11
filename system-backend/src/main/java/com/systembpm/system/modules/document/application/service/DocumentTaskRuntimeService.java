package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TaskDocumentRuntimeResponseDto;

import java.util.Map;

public interface DocumentTaskRuntimeService {
    TaskDocumentRuntimeResponseDto getRuntime(Map<String, Object> taskSnapshot, String requesterEmail);

    void validateBeforeComplete(Map<String, Object> taskSnapshot, String requesterEmail);
}
