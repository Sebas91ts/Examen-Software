package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchResponseDto {
    private List<DocumentMetadataResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String sortBy;
    private String sortDirection;
}
