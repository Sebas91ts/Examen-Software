package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeEditingSessionResponseDto {
    private String documentId;
    private String documentKey;
    private Boolean editing;
    private String currentEditor;
    private Instant editingStartedAt;
    private String message;
}
