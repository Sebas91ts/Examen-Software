package com.systembpm.system.modules.audit.application.dto;

import com.systembpm.system.modules.audit.domain.AuditAction;
import com.systembpm.system.modules.audit.domain.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecordRequest {
    private AuditAction action;
    private AuditEntityType entityType;
    private String entityId;
    private String entityName;
    private String actorEmail;
    private String tenantId;
    private String areaId;
    private String areaName;
    private String processKey;
    private Integer processVersion;
    private String processInstanceId;
    private String taskDefinitionKey;
    private String taskInstanceId;
    private String taskName;
    private String documentId;
    private String documentName;
    private Integer documentVersion;
    private String documentState;
    private String documentRequirementId;
    private String documentRequirementName;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private Map<String, Object> metadata;
}
