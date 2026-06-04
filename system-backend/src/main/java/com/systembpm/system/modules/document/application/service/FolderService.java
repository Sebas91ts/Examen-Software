package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.FolderResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderTreeResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderUpdateRequestDto;

import java.util.List;

public interface FolderService {

    FolderResponseDto create(FolderCreateRequestDto request, String requesterEmail);

    List<FolderResponseDto> list(String requesterEmail);

    FolderResponseDto getById(String id, String requesterEmail);

    FolderResponseDto update(String id, FolderUpdateRequestDto request, String requesterEmail);

    void delete(String id, String requesterEmail);

    List<FolderTreeResponseDto> tree(String requesterEmail);

    List<DocumentMetadataResponseDto> getDocuments(String id, String requesterEmail);
}
