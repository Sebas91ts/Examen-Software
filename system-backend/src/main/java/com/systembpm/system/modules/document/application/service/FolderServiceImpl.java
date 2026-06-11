package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.FolderResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderTreeResponseDto;
import com.systembpm.system.modules.document.application.dto.FolderUpdateRequestDto;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.Folder;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.FolderRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public FolderResponseDto create(FolderCreateRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        denyClientRepositoryAccess(requester);
        validateName(request != null ? request.getName() : null);
        String parentFolderId = normalize(request.getParentFolderId());
        ensureParentExists(parentFolderId, requester.getTenantId());

        Instant now = Instant.now();
        Folder folder = Folder.builder()
                .id(new ObjectId().toHexString())
                .tenantId(requester.getTenantId())
                .name(normalize(request.getName()))
                .description(normalize(request.getDescription()))
                .parentFolderId(parentFolderId)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(requester.getEmail())
                .updatedBy(requester.getEmail())
                .active(true)
                .build();

        Folder saved = folderRepository.save(folder);
        log.info("folder.create tenantId={} folderId={} parentFolderId={} user={}",
                saved.getTenantId(), saved.getId(), saved.getParentFolderId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public List<FolderResponseDto> list(String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        if (isClient(requester)) {
            log.info("folder.list.client-hidden tenantId={} user={}", requester.getTenantId(), requester.getEmail());
            return List.of();
        }
        List<Folder> source = isAdmin(requester)
                ? folderRepository.findByActiveTrueOrderByNameAsc()
                : folderRepository.findByTenantIdAndActiveTrueOrderByNameAsc(requester.getTenantId());
        List<FolderResponseDto> folders = source
                .stream()
                .map(this::toResponse)
                .toList();
        log.info("folder.list tenantId={} user={} count={}", requester.getTenantId(), requester.getEmail(), folders.size());
        return folders;
    }

    @Override
    public FolderResponseDto getById(String id, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        denyClientRepositoryAccess(requester);
        Folder folder = findActiveFolderForRequester(id, requester);
        log.info("folder.read tenantId={} folderId={} user={}", requester.getTenantId(), folder.getId(), requester.getEmail());
        return toResponse(folder);
    }

    @Override
    public FolderResponseDto update(String id, FolderUpdateRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        denyClientRepositoryAccess(requester);
        Folder folder = findActiveFolderForRequester(id, requester);
        validateName(request != null ? request.getName() : null);
        String parentFolderId = normalize(request.getParentFolderId());
        validateParentChange(folder, parentFolderId, requester.getTenantId());

        Instant now = Instant.now();
        folder.setName(normalize(request.getName()));
        folder.setDescription(normalize(request.getDescription()));
        folder.setParentFolderId(parentFolderId);
        folder.setUpdatedAt(now);
        folder.setUpdatedBy(requester.getEmail());

        Folder saved = folderRepository.save(folder);
        log.info("folder.update tenantId={} folderId={} parentFolderId={} user={}",
                saved.getTenantId(), saved.getId(), saved.getParentFolderId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public void delete(String id, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        denyClientRepositoryAccess(requester);
        Folder folder = findActiveFolderForRequester(id, requester);
        Instant now = Instant.now();
        folder.setActive(false);
        folder.setUpdatedAt(now);
        folder.setUpdatedBy(requester.getEmail());
        folderRepository.save(folder);
        log.info("folder.soft-delete tenantId={} folderId={} user={}", requester.getTenantId(), folder.getId(), requester.getEmail());
    }

    @Override
    public List<FolderTreeResponseDto> tree(String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        if (isClient(requester)) {
            log.info("folder.tree.client-hidden tenantId={} user={}", requester.getTenantId(), requester.getEmail());
            return List.of();
        }
        List<Folder> folders = isAdmin(requester)
                ? folderRepository.findByActiveTrueOrderByNameAsc()
                : folderRepository.findByTenantIdAndActiveTrueOrderByNameAsc(requester.getTenantId());
        Map<String, List<Folder>> byParent = new HashMap<>();
        for (Folder folder : folders) {
            byParent.computeIfAbsent(parentKey(folder.getParentFolderId()), ignored -> new ArrayList<>()).add(folder);
        }

        List<FolderTreeResponseDto> tree = buildTree(null, byParent, new HashSet<>());
        log.info("folder.tree tenantId={} user={} roots={} total={}",
                requester.getTenantId(), requester.getEmail(), tree.size(), folders.size());
        return tree;
    }

    @Override
    public List<DocumentMetadataResponseDto> getDocuments(String id, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        denyClientRepositoryAccess(requester);
        Folder folder = findActiveFolderForRequester(id, requester);
        List<DocumentMetadata> source = isAdmin(requester)
                ? documentMetadataRepository.findByFolderIdOrderByUploadedAtDesc(folder.getId())
                : documentMetadataRepository.findByTenantIdAndFolderIdOrderByUploadedAtDesc(requester.getTenantId(), folder.getId());
        List<DocumentMetadataResponseDto> documents = source
                .stream()
                .map(this::toDocumentResponse)
                .toList();
        log.info("folder.documents tenantId={} folderId={} user={} count={}",
                requester.getTenantId(), folder.getId(), requester.getEmail(), documents.size());
        return documents;
    }

    private List<FolderTreeResponseDto> buildTree(String parentFolderId, Map<String, List<Folder>> byParent, Set<String> visited) {
        List<Folder> children = byParent.getOrDefault(parentKey(parentFolderId), List.of()).stream()
                .sorted(Comparator.comparing(folder -> folder.getName() == null ? "" : folder.getName()))
                .toList();

        List<FolderTreeResponseDto> nodes = new ArrayList<>();
        for (Folder child : children) {
            if (!visited.add(child.getId())) {
                log.warn("folder.tree.cycle-detected tenantId={} folderId={} parentFolderId={}",
                        child.getTenantId(), child.getId(), child.getParentFolderId());
                continue;
            }
            FolderTreeResponseDto node = toTreeResponse(child);
            node.setChildren(buildTree(child.getId(), byParent, visited));
            visited.remove(child.getId());
            nodes.add(node);
        }
        return nodes;
    }

    private void validateParentChange(Folder folder, String newParentFolderId, String tenantId) {
        if (isBlank(newParentFolderId)) {
            return;
        }
        if (folder.getId().equals(newParentFolderId)) {
            throw new DocumentValidationException("Una carpeta no puede ser hija de si misma");
        }
        Folder parent = findActiveFolder(newParentFolderId, tenantId);
        Set<String> visited = new HashSet<>();
        String currentParentId = parent.getParentFolderId();
        while (!isBlank(currentParentId)) {
            if (!visited.add(currentParentId)) {
                throw new DocumentValidationException("Se detecto un ciclo en la jerarquia de carpetas");
            }
            if (folder.getId().equals(currentParentId)) {
                throw new DocumentValidationException("No se puede crear un ciclo de carpetas");
            }
            Folder ancestor = findActiveFolder(currentParentId, tenantId);
            currentParentId = ancestor.getParentFolderId();
        }
    }

    private void ensureParentExists(String parentFolderId, String tenantId) {
        if (isBlank(parentFolderId)) {
            return;
        }
        if (!folderRepository.existsByIdAndTenantIdAndActiveTrue(parentFolderId, tenantId)) {
            throw new DocumentValidationException("parentFolderId no existe");
        }
    }

    private Folder findActiveFolder(String id, String tenantId) {
        if (isBlank(id)) {
            throw new DocumentValidationException("folderId es obligatorio");
        }
        return folderRepository.findByIdAndTenantIdAndActiveTrue(id.trim(), tenantId)
                .orElseThrow(() -> new DocumentValidationException("La carpeta no existe o no pertenece a tu tenant"));
    }

    private Folder findActiveFolderForRequester(String id, Usuario requester) {
        if (isAdmin(requester)) {
            if (isBlank(id)) {
                throw new DocumentValidationException("folderId es obligatorio");
            }
            return folderRepository.findByIdAndActiveTrue(id.trim())
                    .orElseThrow(() -> new DocumentValidationException("La carpeta no existe"));
        }
        return findActiveFolder(id, requester.getTenantId());
    }

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = normalize(requesterEmail);
        if (normalized == null) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(usuario.getTenantId())) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private FolderResponseDto toResponse(Folder folder) {
        return FolderResponseDto.builder()
                .id(folder.getId())
                .tenantId(folder.getTenantId())
                .name(folder.getName())
                .description(folder.getDescription())
                .parentFolderId(folder.getParentFolderId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .createdBy(folder.getCreatedBy())
                .updatedBy(folder.getUpdatedBy())
                .active(folder.getActive())
                .build();
    }

    private FolderTreeResponseDto toTreeResponse(Folder folder) {
        return FolderTreeResponseDto.builder()
                .id(folder.getId())
                .tenantId(folder.getTenantId())
                .name(folder.getName())
                .description(folder.getDescription())
                .parentFolderId(folder.getParentFolderId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .createdBy(folder.getCreatedBy())
                .updatedBy(folder.getUpdatedBy())
                .active(folder.getActive())
                .children(new ArrayList<>())
                .build();
    }

    private DocumentMetadataResponseDto toDocumentResponse(DocumentMetadata metadata) {
        return DocumentMetadataResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(DocumentResponseMapper.toAccessRuleDtos(metadata.getAccessRules()))
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
                .editable(metadata.getEditable())
                .collaborativeEditing(metadata.getCollaborativeEditing())
                .onlyOfficeDocumentKey(metadata.getOnlyOfficeDocumentKey())
                .templateDocumentId(metadata.getTemplateDocumentId())
                .currentEditor(metadata.getCurrentEditor())
                .editingStartedAt(metadata.getEditingStartedAt())
                .build();
    }

    private void validateName(String name) {
        if (isBlank(name)) {
            throw new DocumentValidationException("name es obligatorio");
        }
    }

    private String parentKey(String parentFolderId) {
        return isBlank(parentFolderId) ? "__root__" : parentFolderId.trim();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void denyClientRepositoryAccess(Usuario usuario) {
        if (isClient(usuario)) {
            log.warn("folder.client-forbidden tenantId={} user={} roles={}",
                    usuario.getTenantId(), usuario.getEmail(), usuario.getRoles());
            throw new DocumentTenantAccessDeniedException();
        }
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }
}
