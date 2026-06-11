package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentRuntimeRequirementDto {
    private String id;
    private String name;
    private String description;
    private String documentDirection;
    private String documentLifecyclePolicy;
    private Boolean required;
    private Boolean allowUpload;
    private Boolean allowMultipleFiles;
    private Boolean editable;
    private Boolean collaborativeEditing;
    private Boolean requireApproval;
    private Boolean readOnlyAfterComplete;
    private List<String> allowedMimeTypes;
    private Long maxFileSizeBytes;
    private Integer maxFiles;
    private String status;
    private String message;
    private Boolean canView;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canApprove;
    private Boolean canReject;
    private Boolean canLock;
    private List<DocumentMetadataResponseDto> documents;
}
