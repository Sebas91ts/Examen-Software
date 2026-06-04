package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditingSessionResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditorConfigResponseDto;

public interface OnlyOfficePreparationService {

    OnlyOfficeEditorConfigResponseDto getEditorConfig(String documentId, String requesterEmail);

    OnlyOfficeEditingSessionResponseDto startEditing(String documentId, String requesterEmail);

    OnlyOfficeEditingSessionResponseDto finishEditing(String documentId, String requesterEmail);
}
