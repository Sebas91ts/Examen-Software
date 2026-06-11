package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeEditorConfigResponseDto {
    private String documentId;
    private String documentKey;
    private String fileName;
    private String title;
    private String fileType;
    private String mode;
    private Boolean editable;
    private Boolean collaborativeEditing;
    private Boolean readOnlyAfterComplete;
    private Boolean requireApproval;
    private String templateDocumentId;
    private String currentEditor;
    private Instant editingStartedAt;
    private Boolean onlyOfficeReady;
    private String message;
    private String documentServerUrl;
    private String callbackUrl;
    private String token;
    private Map<String, Object> docsApiConfig;
}
