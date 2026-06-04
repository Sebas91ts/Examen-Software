package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TagRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTagServiceImpl implements DocumentTagService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final TagRepository tagRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentResponseMapper documentResponseMapper;

    @Override
    public DocumentMetadataResponseDto addTag(String documentId, String tagId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        ensureTagExists(tagId, requester.getTenantId());

        Set<String> tagIds = new LinkedHashSet<>(document.getTagIds() == null ? List.of() : document.getTagIds());
        tagIds.add(tagId.trim());
        document.setTagIds(new ArrayList<>(tagIds));
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(requester.getEmail());

        DocumentMetadata saved = documentMetadataRepository.save(document);
        log.info("document.tag.add tenantId={} documentId={} tagId={} user={}",
                requester.getTenantId(), documentId, tagId, requester.getEmail());
        return documentResponseMapper.toMetadataResponse(saved);
    }

    @Override
    public DocumentMetadataResponseDto removeTag(String documentId, String tagId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        ensureTagExists(tagId, requester.getTenantId());

        List<String> tagIds = document.getTagIds() == null ? new ArrayList<>() : new ArrayList<>(document.getTagIds());
        tagIds.removeIf(existing -> existing != null && existing.equals(tagId.trim()));
        document.setTagIds(tagIds);
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(requester.getEmail());

        DocumentMetadata saved = documentMetadataRepository.save(document);
        log.info("document.tag.remove tenantId={} documentId={} tagId={} user={}",
                requester.getTenantId(), documentId, tagId, requester.getEmail());
        return documentResponseMapper.toMetadataResponse(saved);
    }

    private DocumentMetadata findDocument(String documentId, String tenantId) {
        if (isBlank(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }
        return documentMetadataRepository.findByIdAndTenantId(documentId.trim(), tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private void ensureTagExists(String tagId, String tenantId) {
        if (isBlank(tagId)) {
            throw new DocumentValidationException("tagId es obligatorio");
        }
        if (!tagRepository.existsByIdAndTenantIdAndActiveTrue(tagId.trim(), tenantId)) {
            throw new DocumentValidationException("El tag no existe o no pertenece a tu tenant");
        }
    }

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = requesterEmail == null ? null : requesterEmail.trim();
        if (isBlank(normalized)) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(usuario.getTenantId())) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
