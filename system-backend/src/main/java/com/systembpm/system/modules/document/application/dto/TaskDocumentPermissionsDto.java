package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentPermissionsDto {
    private Boolean canView;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canDelete;
    private Boolean canApprove;
}

