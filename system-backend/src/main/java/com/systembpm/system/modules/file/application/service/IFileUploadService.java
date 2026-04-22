package com.systembpm.system.modules.file.application.service;

import org.springframework.web.multipart.MultipartFile;
import com.systembpm.system.modules.file.application.dto.FileUploadResponseDto;

public interface IFileUploadService {
    FileUploadResponseDto upload(MultipartFile file);
}
