package com.systembpm.system.modules.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequirement {
    private String id;
    private String name;
    private String description;
    private String documentDirection;
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
    private String ownerAreaId;
    private List<String> allowedAreaIds;
    private List<DocumentAreaAccessRule> accessRules;
    private String documentLifecyclePolicy;
}
