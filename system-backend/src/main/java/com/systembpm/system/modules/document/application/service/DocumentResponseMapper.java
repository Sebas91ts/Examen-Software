package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentAreaAccessRuleDto;
import com.systembpm.system.modules.document.application.dto.TagResponseDto;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.Tag;
import com.systembpm.system.modules.document.infrastructure.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentResponseMapper {

    private final TagRepository tagRepository;

    public DocumentMetadataResponseDto toMetadataResponse(DocumentMetadata metadata) {
        List<TagResponseDto> tags = resolveTags(metadata);
        return DocumentMetadataResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(toAccessRuleDtos(metadata.getAccessRules()))
                .processInstanceId(metadata.getProcessInstanceId())
                .fileName(metadata.getFileName())
                .originalName(metadata.getOriginalName())
                .mimeType(metadata.getMimeType())
                .size(metadata.getSize())
                .s3Key(metadata.getS3Key())
                .uploadedBy(metadata.getUploadedBy())
                .uploadedAt(metadata.getUploadedAt())
                .version(metadata.getVersion())
                .status(metadata.getStatus())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .lastAccessedAt(metadata.getLastAccessedAt())
                .updatedBy(metadata.getUpdatedBy())
                .processKey(metadata.getProcessKey())
                .processVersion(metadata.getProcessVersion())
                .taskDefinitionKey(metadata.getTaskDefinitionKey())
                .taskInstanceId(metadata.getTaskInstanceId())
                .documentRequirementId(metadata.getDocumentRequirementId())
                .documentRequirementName(metadata.getDocumentRequirementName())
                .documentDirection(metadata.getDocumentDirection())
                .documentLifecyclePolicy(metadata.getDocumentLifecyclePolicy())
                .documentState(metadata.getDocumentState())
                .locked(metadata.getLocked())
                .lockedBy(metadata.getLockedBy())
                .lockedAt(metadata.getLockedAt())
                .approvedBy(metadata.getApprovedBy())
                .approvedAt(metadata.getApprovedAt())
                .rejectedBy(metadata.getRejectedBy())
                .rejectedAt(metadata.getRejectedAt())
                .comments(metadata.getComments())
                .folderId(metadata.getFolderId())
                .tagIds(metadata.getTagIds())
                .tags(tags)
                .editable(metadata.getEditable())
                .collaborativeEditing(metadata.getCollaborativeEditing())
                .onlyOfficeDocumentKey(metadata.getOnlyOfficeDocumentKey())
                .templateDocumentId(metadata.getTemplateDocumentId())
                .currentEditor(metadata.getCurrentEditor())
                .editingStartedAt(metadata.getEditingStartedAt())
                .build();
    }

    public DocumentUploadResponseDto toUploadResponse(DocumentMetadata metadata) {
        List<TagResponseDto> tags = resolveTags(metadata);
        return DocumentUploadResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(toAccessRuleDtos(metadata.getAccessRules()))
                .processInstanceId(metadata.getProcessInstanceId())
                .fileName(metadata.getFileName())
                .originalName(metadata.getOriginalName())
                .mimeType(metadata.getMimeType())
                .size(metadata.getSize())
                .s3Key(metadata.getS3Key())
                .uploadedBy(metadata.getUploadedBy())
                .uploadedAt(metadata.getUploadedAt())
                .version(metadata.getVersion())
                .status(metadata.getStatus())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .lastAccessedAt(metadata.getLastAccessedAt())
                .updatedBy(metadata.getUpdatedBy())
                .processKey(metadata.getProcessKey())
                .processVersion(metadata.getProcessVersion())
                .taskDefinitionKey(metadata.getTaskDefinitionKey())
                .taskInstanceId(metadata.getTaskInstanceId())
                .documentRequirementId(metadata.getDocumentRequirementId())
                .documentRequirementName(metadata.getDocumentRequirementName())
                .documentDirection(metadata.getDocumentDirection())
                .documentLifecyclePolicy(metadata.getDocumentLifecyclePolicy())
                .documentState(metadata.getDocumentState())
                .locked(metadata.getLocked())
                .lockedBy(metadata.getLockedBy())
                .lockedAt(metadata.getLockedAt())
                .approvedBy(metadata.getApprovedBy())
                .approvedAt(metadata.getApprovedAt())
                .rejectedBy(metadata.getRejectedBy())
                .rejectedAt(metadata.getRejectedAt())
                .comments(metadata.getComments())
                .folderId(metadata.getFolderId())
                .tagIds(metadata.getTagIds())
                .tags(tags)
                .editable(metadata.getEditable())
                .collaborativeEditing(metadata.getCollaborativeEditing())
                .onlyOfficeDocumentKey(metadata.getOnlyOfficeDocumentKey())
                .templateDocumentId(metadata.getTemplateDocumentId())
                .currentEditor(metadata.getCurrentEditor())
                .editingStartedAt(metadata.getEditingStartedAt())
                .build();
    }

    public TagResponseDto toTagResponse(Tag tag) {
        return TagResponseDto.builder()
                .id(tag.getId())
                .tenantId(tag.getTenantId())
                .name(tag.getName())
                .color(tag.getColor())
                .description(tag.getDescription())
                .active(tag.getActive())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .createdBy(tag.getCreatedBy())
                .updatedBy(tag.getUpdatedBy())
                .build();
    }

    private List<TagResponseDto> resolveTags(DocumentMetadata metadata) {
        if (metadata.getTagIds() == null || metadata.getTagIds().isEmpty()) {
            return List.of();
        }
        return tagRepository.findByTenantIdAndIdInAndActiveTrue(metadata.getTenantId(), metadata.getTagIds()).stream()
                .map(this::toTagResponse)
                .toList();
    }

    public static List<DocumentAreaAccessRuleDto> toAccessRuleDtos(List<DocumentAreaAccessRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> DocumentAreaAccessRuleDto.builder()
                        .areaId(rule.getAreaId())
                        .canView(rule.getCanView())
                        .canUpload(rule.getCanUpload())
                        .canEdit(rule.getCanEdit())
                        .canDownload(rule.getCanDownload())
                        .canApprove(rule.getCanApprove())
                        .canReject(rule.getCanReject())
                        .canLock(rule.getCanLock())
                        .build())
                .toList();
    }
}
