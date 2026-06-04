package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TagCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.TagResponseDto;
import com.systembpm.system.modules.document.application.dto.TagUpdateRequestDto;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.Tag;
import com.systembpm.system.modules.document.infrastructure.repository.TagRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentResponseMapper documentResponseMapper;

    @Override
    public TagResponseDto create(TagCreateRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        validateName(request != null ? request.getName() : null);
        String name = normalize(request.getName());
        if (tagRepository.existsByTenantIdAndNameIgnoreCaseAndActiveTrue(requester.getTenantId(), name)) {
            throw new DocumentValidationException("Ya existe un tag activo con ese nombre");
        }

        Instant now = Instant.now();
        Tag tag = Tag.builder()
                .id(new ObjectId().toHexString())
                .tenantId(requester.getTenantId())
                .name(name)
                .color(normalize(request.getColor()))
                .description(normalize(request.getDescription()))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(requester.getEmail())
                .updatedBy(requester.getEmail())
                .build();

        Tag saved = tagRepository.save(tag);
        log.info("tag.create tenantId={} tagId={} user={}", saved.getTenantId(), saved.getId(), requester.getEmail());
        return documentResponseMapper.toTagResponse(saved);
    }

    @Override
    public List<TagResponseDto> list(String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        List<TagResponseDto> tags = tagRepository.findByTenantIdAndActiveTrueOrderByNameAsc(requester.getTenantId()).stream()
                .map(documentResponseMapper::toTagResponse)
                .toList();
        log.info("tag.list tenantId={} user={} count={}", requester.getTenantId(), requester.getEmail(), tags.size());
        return tags;
    }

    @Override
    public TagResponseDto getById(String id, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        Tag tag = findActiveTag(id, requester.getTenantId());
        log.info("tag.read tenantId={} tagId={} user={}", requester.getTenantId(), tag.getId(), requester.getEmail());
        return documentResponseMapper.toTagResponse(tag);
    }

    @Override
    public TagResponseDto update(String id, TagUpdateRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        Tag tag = findActiveTag(id, requester.getTenantId());
        validateName(request != null ? request.getName() : null);

        String name = normalize(request.getName());
        boolean nameChanged = tag.getName() == null || !tag.getName().equalsIgnoreCase(name);
        if (nameChanged && tagRepository.existsByTenantIdAndNameIgnoreCaseAndActiveTrue(requester.getTenantId(), name)) {
            throw new DocumentValidationException("Ya existe un tag activo con ese nombre");
        }

        tag.setName(name);
        tag.setColor(normalize(request.getColor()));
        tag.setDescription(normalize(request.getDescription()));
        tag.setUpdatedAt(Instant.now());
        tag.setUpdatedBy(requester.getEmail());

        Tag saved = tagRepository.save(tag);
        log.info("tag.update tenantId={} tagId={} user={}", saved.getTenantId(), saved.getId(), requester.getEmail());
        return documentResponseMapper.toTagResponse(saved);
    }

    @Override
    public void delete(String id, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        Tag tag = findActiveTag(id, requester.getTenantId());
        tag.setActive(false);
        tag.setUpdatedAt(Instant.now());
        tag.setUpdatedBy(requester.getEmail());
        tagRepository.save(tag);
        log.info("tag.soft-delete tenantId={} tagId={} user={}", requester.getTenantId(), tag.getId(), requester.getEmail());
    }

    private Tag findActiveTag(String id, String tenantId) {
        if (isBlank(id)) {
            throw new DocumentValidationException("tagId es obligatorio");
        }
        return tagRepository.findByIdAndTenantIdAndActiveTrue(id.trim(), tenantId)
                .orElseThrow(() -> new DocumentValidationException("El tag no existe o no pertenece a tu tenant"));
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

    private void validateName(String name) {
        if (isBlank(name)) {
            throw new DocumentValidationException("name es obligatorio");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
