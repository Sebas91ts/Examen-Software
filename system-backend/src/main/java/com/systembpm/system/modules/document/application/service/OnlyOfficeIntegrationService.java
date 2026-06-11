package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackRequestDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditingSessionResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditorConfigResponseDto;
import com.systembpm.system.modules.document.domain.DocumentMetadata;

public interface OnlyOfficeIntegrationService {

    OnlyOfficeEditorConfigResponseDto getEditorConfig(String documentId, String requesterEmail);

    OnlyOfficeEditingSessionResponseDto startEditing(String documentId, String requesterEmail);

    OnlyOfficeEditingSessionResponseDto finishEditing(String documentId, String requesterEmail);

    OnlyOfficeCallbackResponseDto handleCallback(OnlyOfficeCallbackRequestDto request, String authorizationHeader);

    DocumentMetadata validateContentToken(String documentId, String token);

    byte[] downloadContent(String documentId, String token);
}
