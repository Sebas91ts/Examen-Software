package com.systembpm.system.modules.document.application.dto;

import com.systembpm.system.modules.document.domain.DocumentStatus;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadataResponseDto {
    private String id;
    private String tenantId;
    private String ownerAreaId;
    private List<String> allowedAreaIds;
    private List<DocumentAreaAccessRuleDto> accessRules;
    private String processInstanceId;
    private String fileName;
    private String originalName;
    private String mimeType;
    private Long size;
    private String s3Key;
    private String uploadedBy;
    private Instant uploadedAt;
    private Integer version;
    private DocumentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastAccessedAt;
    private String updatedBy;
    private String processKey;
    private Integer processVersion;
    private String taskDefinitionKey;
    private String taskInstanceId;
    private String documentRequirementId;
    private String documentRequirementName;
    private String documentDirection;
    private String documentLifecyclePolicy;
    private DocumentLifecycleState documentState;
    private Boolean locked;
    private String lockedBy;
    private Instant lockedAt;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectedBy;
    private Instant rejectedAt;
    private List<String> comments;
    private String folderId;
    private List<String> tagIds;
    private List<TagResponseDto> tags;
    private Boolean editable;
    private Boolean collaborativeEditing;
    private String onlyOfficeDocumentKey;
    private String templateDocumentId;
    private String currentEditor;
    private Instant editingStartedAt;
}
