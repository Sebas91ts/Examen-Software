package com.systembpm.system.modules.file.application.service;

import com.systembpm.system.modules.file.application.dto.FileUploadResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements IFileUploadService {

    private final LegacyFileStorageService legacyFileStorageService;

    @Override
    public FileUploadResponseDto upload(MultipartFile file) {
        return legacyFileStorageService.upload(file);
    }
}
