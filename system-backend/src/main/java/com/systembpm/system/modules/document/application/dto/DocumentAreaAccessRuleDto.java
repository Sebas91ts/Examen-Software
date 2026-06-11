package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAreaAccessRuleDto {
    private String areaId;
    private Boolean canView;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canDownload;
    private Boolean canApprove;
    private Boolean canReject;
    private Boolean canLock;
}
