package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentDownloadUrlResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentUploadResponseDto upload(DocumentUploadRequestDto request, MultipartFile file, String uploadedBy);

    DocumentMetadataResponseDto getById(String documentId, String requester);

    DocumentDownloadUrlResponseDto getDownloadUrl(String documentId, String requester);

    List<DocumentMetadataResponseDto> getByProcessInstanceId(String processInstanceId, String requester);
}
