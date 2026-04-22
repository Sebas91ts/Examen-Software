package com.systembpm.system.modules.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDto {
    private String publicId;
    private String fileName;
    private String secureUrl;
    private String mimeType;
    private Long size;
    private String resourceType;
}
