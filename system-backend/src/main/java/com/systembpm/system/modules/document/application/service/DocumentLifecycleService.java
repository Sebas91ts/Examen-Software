package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;

import java.util.List;
import java.util.Map;

public interface DocumentLifecycleService {

    DocumentMetadataResponseDto approve(String documentId, String comment, String requesterEmail);

    DocumentMetadataResponseDto reject(String documentId, String comment, String requesterEmail);

    DocumentMetadataResponseDto lock(String documentId, String requesterEmail);

    DocumentMetadataResponseDto unlock(String documentId, String requesterEmail);

    List<DocumentMetadataResponseDto> getByTask(
            String processInstanceId,
            String taskDefinitionKey,
            String taskInstanceId,
            String requesterEmail
    );

    List<DocumentMetadataResponseDto> getPending(String processInstanceId, String requesterEmail);

    void onTaskCompleted(Map<String, Object> taskSnapshot, String completedBy);
}
