package com.systembpm.system.modules.audit.application.service;

import com.systembpm.system.modules.audit.application.dto.AuditEventResponseDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchRequestDto;
import com.systembpm.system.modules.audit.application.dto.AuditEventSearchResponseDto;
import com.systembpm.system.modules.audit.application.dto.AuditRecordRequest;
import com.systembpm.system.modules.audit.domain.AuditEvent;
import com.systembpm.system.modules.audit.infrastructure.repository.AuditEventRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private final AuditEventRepository auditEventRepository;
    private final UsuarioRepository usuarioRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void record(AuditRecordRequest request) {
        if (request == null || request.getAction() == null || request.getEntityType() == null) {
            log.debug("audit.record.skip reason=missing-required-fields");
            return;
        }

        try {
            Usuario actor = resolveActor(request.getActorEmail());
            AuditEvent event = AuditEvent.builder()
                    .action(request.getAction())
                    .entityType(request.getEntityType())
                    .entityId(trim(request.getEntityId()))
                    .entityName(trim(request.getEntityName()))
                    .actorId(actor != null ? actor.getId() : null)
                    .actorEmail(actor != null ? actor.getEmail() : trim(request.getActorEmail()))
                    .actorName(resolveActorName(actor))
                    .actorRoles(actor != null ? actor.getRoles() : List.of())
                    .tenantId(firstText(request.getTenantId(), actor != null ? actor.getTenantId() : null))
                    .areaId(firstText(request.getAreaId(), effectiveAreaId(actor)))
                    .areaName(firstText(request.getAreaName(), actor != null ? actor.getAreaNombre() : null))
                    .processKey(trim(request.getProcessKey()))
                    .processVersion(request.getProcessVersion())
                    .processInstanceId(trim(request.getProcessInstanceId()))
                    .taskDefinitionKey(trim(request.getTaskDefinitionKey()))
                    .taskInstanceId(trim(request.getTaskInstanceId()))
                    .taskName(trim(request.getTaskName()))
                    .documentId(trim(request.getDocumentId()))
                    .documentName(trim(request.getDocumentName()))
                    .documentVersion(request.getDocumentVersion())
                    .documentState(trim(request.getDocumentState()))
                    .documentRequirementId(trim(request.getDocumentRequirementId()))
                    .documentRequirementName(trim(request.getDocumentRequirementName()))
                    .beforeSnapshot(sanitizeMap(request.getBeforeSnapshot()))
                    .afterSnapshot(sanitizeMap(request.getAfterSnapshot()))
                    .metadata(sanitizeMap(request.getMetadata()))
                    .createdAt(Instant.now())
                    .build();
            auditEventRepository.save(event);
            log.info("audit.event.recorded action={} entityType={} entityId={} actor={} areaId={} processInstanceId={} documentId={}",
                    event.getAction(), event.getEntityType(), event.getEntityId(), event.getActorEmail(),
                    event.getAreaId(), event.getProcessInstanceId(), event.getDocumentId());
        } catch (Exception ex) {
            log.warn("audit.event.record-failed action={} entityType={} entityId={} actor={}",
                    request.getAction(), request.getEntityType(), request.getEntityId(), request.getActorEmail(), ex);
        }
    }

    @Override
    public AuditEventSearchResponseDto search(AuditEventSearchRequestDto request, String requesterEmail) {
        Usuario requester = requireRequester(requesterEmail);
        AuditEventSearchRequestDto safeRequest = request == null ? new AuditEventSearchRequestDto() : request;
        int page = safeRequest.getPage() == null || safeRequest.getPage() < 0 ? DEFAULT_PAGE : safeRequest.getPage();
        int size = safeRequest.getSize() == null || safeRequest.getSize() <= 0 ? DEFAULT_SIZE : Math.min(safeRequest.getSize(), MAX_SIZE);

        Query query = buildQuery(safeRequest, requester);
        long total = mongoTemplate.count(query, AuditEvent.class);
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AuditEventResponseDto> content = mongoTemplate.find(query, AuditEvent.class)
                .stream()
                .map(this::toResponse)
                .toList();

        return AuditEventSearchResponseDto.builder()
                .content(content)
                .totalElements(total)
                .page(page)
                .size(size)
                .totalPages(size == 0 ? 0 : (int) Math.ceil((double) total / size))
                .build();
    }

    @Override
    public List<AuditEventResponseDto> listByDocument(String documentId, String requesterEmail) {
        AuditEventSearchRequestDto request = AuditEventSearchRequestDto.builder()
                .documentId(documentId)
                .size(MAX_SIZE)
                .build();
        return search(request, requesterEmail).getContent();
    }

    @Override
    public List<AuditEventResponseDto> listByProcessInstance(String processInstanceId, String requesterEmail) {
        AuditEventSearchRequestDto request = AuditEventSearchRequestDto.builder()
                .processInstanceId(processInstanceId)
                .size(MAX_SIZE)
                .build();
        return search(request, requesterEmail).getContent();
    }

    @Override
    public List<AuditEventResponseDto> listByTask(String taskInstanceId, String requesterEmail) {
        AuditEventSearchRequestDto request = AuditEventSearchRequestDto.builder()
                .taskInstanceId(taskInstanceId)
                .size(MAX_SIZE)
                .build();
        return search(request, requesterEmail).getContent();
    }

    private Query buildQuery(AuditEventSearchRequestDto request, Usuario requester) {
        List<Criteria> parts = new java.util.ArrayList<>();

        if (!isAdmin(requester)) {
            String areaId = effectiveAreaId(requester);
            if (isClient(requester)) {
                parts.add(Criteria.where("actorEmail").is(requester.getEmail()));
            } else {
                parts.add(new Criteria().orOperator(
                        Criteria.where("areaId").is(areaId),
                        Criteria.where("actorEmail").is(requester.getEmail())
                ));
            }
        } else if (hasText(request.getAreaId())) {
            parts.add(Criteria.where("areaId").is(request.getAreaId().trim()));
        }

        if (request.getAction() != null) {
            parts.add(Criteria.where("action").is(request.getAction()));
        }
        if (request.getEntityType() != null) {
            parts.add(Criteria.where("entityType").is(request.getEntityType()));
        }
        addEquals(parts, "entityId", request.getEntityId());
        addEquals(parts, "actorEmail", request.getActorEmail());
        addEquals(parts, "processInstanceId", request.getProcessInstanceId());
        addEquals(parts, "processKey", request.getProcessKey());
        addEquals(parts, "taskInstanceId", request.getTaskInstanceId());
        addEquals(parts, "taskDefinitionKey", request.getTaskDefinitionKey());
        addEquals(parts, "documentId", request.getDocumentId());
        addDateRange(parts, request.getCreatedAtFrom(), request.getCreatedAtTo());

        Query query = new Query();
        if (!parts.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(parts.toArray(Criteria[]::new)));
        }
        return query;
    }

    private void addEquals(List<Criteria> parts, String field, String value) {
        if (hasText(value)) {
            parts.add(Criteria.where(field).is(value.trim()));
        }
    }

    private void addDateRange(List<Criteria> parts, String from, String to) {
        Instant start = parseInstant(from);
        Instant end = parseInstant(to);
        if (start == null && end == null) {
            return;
        }
        Criteria criteria = Criteria.where("createdAt");
        if (start != null) {
            criteria = criteria.gte(start);
        }
        if (end != null) {
            criteria = criteria.lte(end);
        }
        parts.add(criteria);
    }

    private AuditEventResponseDto toResponse(AuditEvent event) {
        return AuditEventResponseDto.builder()
                .id(event.getId())
                .action(event.getAction())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .entityName(event.getEntityName())
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .actorName(event.getActorName())
                .actorRoles(event.getActorRoles())
                .tenantId(event.getTenantId())
                .areaId(event.getAreaId())
                .areaName(event.getAreaName())
                .processKey(event.getProcessKey())
                .processVersion(event.getProcessVersion())
                .processInstanceId(event.getProcessInstanceId())
                .taskDefinitionKey(event.getTaskDefinitionKey())
                .taskInstanceId(event.getTaskInstanceId())
                .taskName(event.getTaskName())
                .documentId(event.getDocumentId())
                .documentName(event.getDocumentName())
                .documentVersion(event.getDocumentVersion())
                .documentState(event.getDocumentState())
                .documentRequirementId(event.getDocumentRequirementId())
                .documentRequirementName(event.getDocumentRequirementName())
                .beforeSnapshot(event.getBeforeSnapshot())
                .afterSnapshot(event.getAfterSnapshot())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private Usuario requireRequester(String requesterEmail) {
        String normalized = trim(requesterEmail);
        if (!hasText(normalized)) {
            throw new IllegalArgumentException("Usuario autenticado requerido");
        }
        return usuarioRepository.findByEmail(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
    }

    private Usuario resolveActor(String actorEmail) {
        String normalized = trim(actorEmail);
        if (!hasText(normalized)) {
            return null;
        }
        return usuarioRepository.findByEmail(normalized).orElse(null);
    }

    private String resolveActorName(Usuario actor) {
        if (actor == null) {
            return null;
        }
        String fullName = firstText(actor.getNombre(), "") + " " + firstText(actor.getApellido(), "");
        return fullName.trim().isBlank() ? actor.getEmail() : fullName.trim();
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(String key, Object value) {
        if (key != null && key.matches("(?i).*(password|token|secret|jwt|authorization).*")) {
            return "<masked>";
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> result = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> result.put(String.valueOf(nestedKey), sanitizeValue(String.valueOf(nestedKey), nestedValue)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> item instanceof Map<?, ?> map ? sanitizeValue(key, map) : item).toList();
        }
        return value;
    }

    private Instant parseInstant(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            log.debug("audit.search.invalid-date value={}", value);
            return null;
        }
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "CLIENT".equalsIgnoreCase(role) || "ROLE_CLIENT".equalsIgnoreCase(role));
    }

    private String effectiveAreaId(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        if (hasText(usuario.getAreaId())) {
            return usuario.getAreaId().trim();
        }
        return hasText(usuario.getTenantId()) ? usuario.getTenantId().trim() : null;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : trim(second);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
