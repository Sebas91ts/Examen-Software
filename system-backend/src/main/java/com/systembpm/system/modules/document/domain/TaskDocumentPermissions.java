package com.systembpm.system.modules.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocumentPermissions {
    private Boolean canView;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canDelete;
    private Boolean canApprove;
}

