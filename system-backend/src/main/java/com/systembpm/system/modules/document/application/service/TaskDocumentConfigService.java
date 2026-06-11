package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigCreateDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;

import java.util.Optional;

public interface TaskDocumentConfigService {
    TaskDocumentConfigResponseDto save(TaskDocumentConfigCreateDto dto, String requesterEmail);

    Optional<TaskDocumentConfigResponseDto> get(String processKey, Integer processVersion, String taskDefinitionKey, String requesterEmail);

    TaskDocumentUploadValidationResponseDto validateUpload(TaskDocumentUploadValidationRequestDto dto, String requesterEmail);
}

