package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TagCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.TagResponseDto;
import com.systembpm.system.modules.document.application.dto.TagUpdateRequestDto;

import java.util.List;

public interface TagService {

    TagResponseDto create(TagCreateRequestDto request, String requesterEmail);

    List<TagResponseDto> list(String requesterEmail);

    TagResponseDto getById(String id, String requesterEmail);

    TagResponseDto update(String id, TagUpdateRequestDto request, String requesterEmail);

    void delete(String id, String requesterEmail);
}
