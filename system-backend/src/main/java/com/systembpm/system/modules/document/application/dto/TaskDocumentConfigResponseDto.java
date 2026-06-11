package com.systembpm.system.modules.document.application.dto;

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
public class TaskDocumentConfigResponseDto {
    private String id;
    private String tenantId;
    private String processKey;
    private Integer processVersion;
    private String taskDefinitionKey;
    private List<DocumentRequirementDto> documentRequirements;
    private String documentName;
    private String description;
    private String documentDirection;
    private Boolean required;
    private Boolean allowMultipleFiles;
    private Boolean allowUpload;
    private Boolean editable;
    private Boolean allowEditing;
    private Boolean collaborativeEditing;
    private Boolean allowVersioning;
    private List<String> allowedMimeTypes;
    private Long maxFileSizeBytes;
    private Integer maxFiles;
    private Boolean readOnlyAfterComplete;
    private Boolean requireApproval;
    private String templateDocumentId;
    private TaskDocumentPermissionsDto permissions;
    private String ownerAreaId;
    private List<String> allowedAreaIds;
    private List<DocumentAreaAccessRuleDto> accessRules;
    private Boolean shareWithNextArea;
    private Boolean autoGenerateOnTaskStart;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
