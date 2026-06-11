package com.systembpm.system.modules.audit.application.service;

import com.systembpm.system.modules.audit.application.dto.AuditEventResponseDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchRequestDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchResponseDto;
import com.systembpm.system.modules.audit.application.dto.AuditRecordRequest;

import java.util.List;

public interface AuditService {

    void record(AuditRecordRequest request);

    AuditEventSearchResponseDto search(AuditEventSearchRequestDto request, String requesterEmail);

    List<AuditEventResponseDto> listByDocument(String documentId, String requesterEmail);

    List<AuditEventResponseDto> listByProcessInstance(String processInstanceId, String requesterEmail);

    List<AuditEventResponseDto> listByTask(String taskInstanceId, String requesterEmail);
}
