package com.systembpm.system.modules.document.application.dto;

import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchRequestDto {
    private String folderId;
    private String tagId;
    private DocumentLifecycleState documentState;
    private String mimeType;
    private String uploadedBy;
    private String processKey;
    private Integer processVersion;
    private String taskDefinitionKey;
    private String originalName;
    private String createdAtFrom;
    private String createdAtTo;
    private String uploadedAtFrom;
    private String uploadedAtTo;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
