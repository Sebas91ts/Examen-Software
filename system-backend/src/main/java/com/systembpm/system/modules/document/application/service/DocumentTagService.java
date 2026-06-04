package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;

public interface DocumentTagService {

    DocumentMetadataResponseDto addTag(String documentId, String tagId, String requesterEmail);

    DocumentMetadataResponseDto removeTag(String documentId, String tagId, String requesterEmail);
}
