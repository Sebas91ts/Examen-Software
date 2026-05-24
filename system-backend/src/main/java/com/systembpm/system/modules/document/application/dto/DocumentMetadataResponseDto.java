package com.systembpm.system.modules.document.application.dto;

import com.systembpm.system.modules.document.domain.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadataResponseDto {
    private String id;
    private String tenantId;
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
}
