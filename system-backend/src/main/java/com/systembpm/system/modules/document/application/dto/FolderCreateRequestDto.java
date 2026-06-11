package com.systembpm.system.modules.document.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderCreateRequestDto {

    @NotBlank(message = "name es obligatorio")
    private String name;

    private String description;

    private String parentFolderId;
}
