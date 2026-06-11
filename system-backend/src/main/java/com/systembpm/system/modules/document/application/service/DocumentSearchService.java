package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentSearchRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentSearchResponseDto;

public interface DocumentSearchService {

    DocumentSearchResponseDto search(DocumentSearchRequestDto request, String requesterEmail);
}
