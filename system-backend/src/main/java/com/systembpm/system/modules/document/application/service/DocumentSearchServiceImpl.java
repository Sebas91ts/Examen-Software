package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentSearchRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentSearchResponseDto;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSearchServiceImpl implements DocumentSearchService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "uploadedAt",
            "createdAt",
            "updatedAt",
            "originalName",
            "mimeType",
            "uploadedBy",
            "documentState"
    );

    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarioRepository;
    private final DocumentResponseMapper documentResponseMapper;

    @Override
    public DocumentSearchResponseDto search(DocumentSearchRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentSearchRequestDto filters = request == null ? new DocumentSearchRequestDto() : request;

        int page = filters.getPage() == null ? DEFAULT_PAGE : Math.max(0, filters.getPage());
        int size = filters.getSize() == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, filters.getSize()));
        String sortBy = resolveSortBy(filters.getSortBy());
        Sort.Direction direction = resolveDirection(filters.getSortDirection());

        String requesterAreaId = effectiveAreaId(requester);
        Criteria criteria = new Criteria();
        if (isAdmin(requester)) {
            if (!isBlank(filters.getTenantId())) {
                criteria.and("tenantId").is(filters.getTenantId().trim());
            }
        } else if (isClient(requester)) {
            criteria.and("uploadedBy").is(requester.getEmail());
        } else {
            criteria = new Criteria().orOperator(
                    Criteria.where("tenantId").is(requesterAreaId),
                    Criteria.where("ownerAreaId").is(requesterAreaId),
                    Criteria.where("allowedAreaIds").is(requesterAreaId),
                    Criteria.where("accessRules").elemMatch(new Criteria().andOperator(
                            Criteria.where("areaId").is(requesterAreaId),
                            Criteria.where("canView").is(true)
                    ))
            );
        }
        applyFilters(criteria, filters);

        Query countQuery = new Query(criteria);
        long total = mongoTemplate.count(countQuery, DocumentMetadata.class);

        Query query = new Query(criteria)
                .with(Sort.by(direction, sortBy))
                .skip((long) page * size)
                .limit(size);
        List<DocumentMetadata> documents = mongoTemplate.find(query, DocumentMetadata.class);

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        log.info("document.search areaId={} tenantId={} user={} roles={} page={} size={} total={} sortBy={} sortDirection={}",
                requesterAreaId, requester.getTenantId(), requester.getEmail(), requester.getRoles(), page, size, total, sortBy, direction.name());

        return DocumentSearchResponseDto.builder()
                .content(documents.stream().map(documentResponseMapper::toMetadataResponse).toList())
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .sortBy(sortBy)
                .sortDirection(direction.name())
                .build();
    }

    private void applyFilters(Criteria criteria, DocumentSearchRequestDto filters) {
        if (!isBlank(filters.getFolderId())) {
            criteria.and("folderId").is(filters.getFolderId().trim());
        }
        if (!isBlank(filters.getTagId())) {
            criteria.and("tagIds").is(filters.getTagId().trim());
        }
        if (filters.getDocumentState() != null) {
            criteria.and("documentState").is(filters.getDocumentState());
        }
        if (!isBlank(filters.getMimeType())) {
            criteria.and("mimeType").is(filters.getMimeType().trim().toLowerCase());
        }
        if (!isBlank(filters.getUploadedBy())) {
            criteria.and("uploadedBy").is(filters.getUploadedBy().trim());
        }
        if (!isBlank(filters.getProcessInstanceId())) {
            criteria.and("processInstanceId").is(filters.getProcessInstanceId().trim());
        }
        if (!isBlank(filters.getProcessKey())) {
            criteria.and("processKey").is(filters.getProcessKey().trim());
        }
        if (filters.getProcessVersion() != null) {
            criteria.and("processVersion").is(filters.getProcessVersion());
        }
        if (!isBlank(filters.getTaskDefinitionKey())) {
            criteria.and("taskDefinitionKey").is(filters.getTaskDefinitionKey().trim());
        }
        if (!isBlank(filters.getOriginalName())) {
            String escaped = Pattern.quote(filters.getOriginalName().trim());
            criteria.and("originalName").regex(".*" + escaped + ".*", "i");
        }
        applyRange(criteria, "createdAt", filters.getCreatedAtFrom(), filters.getCreatedAtTo());
        applyRange(criteria, "uploadedAt", filters.getUploadedAtFrom(), filters.getUploadedAtTo());
    }

    private void applyRange(Criteria criteria, String field, String from, String to) {
        Instant fromInstant = parseInstant(from, field + " desde");
        Instant toInstant = parseInstant(to, field + " hasta");
        if (fromInstant == null && toInstant == null) {
            return;
        }
        Criteria range = criteria.and(field);
        if (fromInstant != null && toInstant != null) {
            range.gte(fromInstant).lte(toInstant);
            return;
        }
        if (fromInstant != null) {
            range.gte(fromInstant);
            return;
        }
        range.lte(toInstant);
    }

    private Instant parseInstant(String value, String label) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new DocumentValidationException(label + " debe estar en formato ISO-8601, por ejemplo 2026-05-31T00:00:00Z");
        }
    }

    private String resolveSortBy(String sortBy) {
        if (isBlank(sortBy)) {
            return "uploadedAt";
        }
        String normalized = sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(normalized)) {
            throw new DocumentValidationException("sortBy no permitido");
        }
        return normalized;
    }

    private Sort.Direction resolveDirection(String direction) {
        if (isBlank(direction)) {
            return Sort.Direction.DESC;
        }
        return "ASC".equalsIgnoreCase(direction.trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = requesterEmail == null ? null : requesterEmail.trim();
        if (isBlank(normalized)) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(effectiveAreaId(usuario)) && !isAdmin(usuario) && !isClient(usuario)) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    private String effectiveAreaId(Usuario usuario) {
        if (!isBlank(usuario.getAreaId())) {
            return usuario.getAreaId().trim();
        }
        return isBlank(usuario.getTenantId()) ? null : usuario.getTenantId().trim();
    }
}
