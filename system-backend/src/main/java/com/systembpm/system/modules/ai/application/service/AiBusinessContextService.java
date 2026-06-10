package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.modules.ai.application.dto.AiBusinessContextPayloadDto;
import com.systembpm.system.modules.ai.application.dto.AiBusinessContextRequestDto;

public interface AiBusinessContextService {
    AiBusinessContextPayloadDto buildContext(AiBusinessContextRequestDto request, String requesterEmail);
}
