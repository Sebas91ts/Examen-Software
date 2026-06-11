package com.systembpm.system.modules.audit.application.dto;

import com.systembpm.system.modules.audit.domain.AuditAction;
import com.systembpm.system.modules.audit.domain.AuditEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventSearchRequestDto {
    private AuditAction action;
    private AuditEntityType entityType;
    private String entityId;
    private String actorEmail;
    private String areaId;
    private String processInstanceId;
    private String processKey;
    private String taskInstanceId;
    private String taskDefinitionKey;
    private String documentId;
    private String createdAtFrom;
    private String createdAtTo;
    private Integer page;
    private Integer size;
}
