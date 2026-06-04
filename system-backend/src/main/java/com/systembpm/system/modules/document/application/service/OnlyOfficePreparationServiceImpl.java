package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditingSessionResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditorConfigResponseDto;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.InvalidDocumentAccessException;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficePreparationServiceImpl implements OnlyOfficePreparationService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public OnlyOfficeEditorConfigResponseDto getEditorConfig(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        TaskDocumentConfig config = resolveTaskConfig(document);
        ensureDocumentKey(document);
        applyDocumentEditingDefaults(document, config, requester.getEmail());
        DocumentMetadata saved = documentMetadataRepository.save(document);

        log.info("onlyoffice.config tenantId={} documentId={} user={} editable={} collaborative={}",
                requester.getTenantId(), documentId, requester.getEmail(), saved.getEditable(), saved.getCollaborativeEditing());
        return buildEditorConfig(saved, config);
    }

    @Override
    public OnlyOfficeEditingSessionResponseDto startEditing(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        TaskDocumentConfig config = resolveTaskConfig(document);
        ensureCanEdit(document, config, requester.getEmail());
        ensureDocumentKey(document);

        Instant now = Instant.now();
        document.setCurrentEditor(requester.getEmail());
        document.setEditingStartedAt(now);
        document.setUpdatedAt(now);
        document.setUpdatedBy(requester.getEmail());
        DocumentMetadata saved = documentMetadataRepository.save(document);

        log.info("onlyoffice.start-editing tenantId={} documentId={} user={} documentKey={}",
                requester.getTenantId(), documentId, requester.getEmail(), saved.getOnlyOfficeDocumentKey());
        return OnlyOfficeEditingSessionResponseDto.builder()
                .documentId(saved.getId())
                .documentKey(saved.getOnlyOfficeDocumentKey())
                .editing(true)
                .currentEditor(saved.getCurrentEditor())
                .editingStartedAt(saved.getEditingStartedAt())
                .message("Sesion de edicion preparada para futura integracion OnlyOffice")
                .build();
    }

    @Override
    public OnlyOfficeEditingSessionResponseDto finishEditing(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        if (document.getCurrentEditor() != null && !document.getCurrentEditor().equalsIgnoreCase(requester.getEmail()) && !isAdmin(requester)) {
            throw new InvalidDocumentAccessException("Solo el editor actual o un administrador puede finalizar la edicion");
        }

        Instant now = Instant.now();
        document.setCurrentEditor(null);
        document.setEditingStartedAt(null);
        document.setUpdatedAt(now);
        document.setUpdatedBy(requester.getEmail());
        DocumentMetadata saved = documentMetadataRepository.save(document);

        log.info("onlyoffice.finish-editing tenantId={} documentId={} user={}",
                requester.getTenantId(), documentId, requester.getEmail());
        return OnlyOfficeEditingSessionResponseDto.builder()
                .documentId(saved.getId())
                .documentKey(saved.getOnlyOfficeDocumentKey())
                .editing(false)
                .currentEditor(saved.getCurrentEditor())
                .editingStartedAt(saved.getEditingStartedAt())
                .message("Sesion de edicion finalizada")
                .build();
    }

    private void ensureCanEdit(DocumentMetadata document, TaskDocumentConfig config, String requesterEmail) {
        applyDocumentEditingDefaults(document, config, requesterEmail);
        if (!Boolean.TRUE.equals(document.getEditable())) {
            throw new InvalidDocumentAccessException("El documento no esta habilitado para edicion");
        }
        if (document.getDocumentState() == DocumentLifecycleState.FINAL || document.getDocumentState() == DocumentLifecycleState.ARCHIVED) {
            throw new InvalidDocumentAccessException("El documento esta en estado final y no puede editarse");
        }
        if (Boolean.TRUE.equals(document.getLocked()) && "workflow".equalsIgnoreCase(nullSafe(document.getLockedBy()))) {
            throw new InvalidDocumentAccessException("El documento fue bloqueado por avance del workflow");
        }
        if (!Boolean.TRUE.equals(document.getCollaborativeEditing())
                && document.getCurrentEditor() != null
                && !document.getCurrentEditor().equalsIgnoreCase(requesterEmail)) {
            throw new InvalidDocumentAccessException("El documento ya esta siendo editado por otro usuario");
        }
    }

    private void applyDocumentEditingDefaults(DocumentMetadata document, TaskDocumentConfig config, String updatedBy) {
        boolean allowEditing = config != null
                ? Boolean.TRUE.equals(config.getAllowEditing()) || Boolean.TRUE.equals(config.getEditable())
                : Boolean.TRUE.equals(document.getEditable());
        boolean collaborative = config != null
                ? Boolean.TRUE.equals(config.getCollaborativeEditing())
                : Boolean.TRUE.equals(document.getCollaborativeEditing());

        if (document.getEditable() == null || config != null) {
            document.setEditable(allowEditing);
        }
        if (document.getCollaborativeEditing() == null || config != null) {
            document.setCollaborativeEditing(collaborative);
        }
        if (document.getTemplateDocumentId() == null && config != null) {
            document.setTemplateDocumentId(config.getTemplateDocumentId());
        }
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(updatedBy);
    }

    private void ensureDocumentKey(DocumentMetadata document) {
        if (document.getOnlyOfficeDocumentKey() != null && !document.getOnlyOfficeDocumentKey().isBlank()) {
            return;
        }
        String version = document.getVersion() == null ? "1" : String.valueOf(document.getVersion());
        document.setOnlyOfficeDocumentKey(document.getTenantId() + "-" + document.getId() + "-v" + version);
    }

    private OnlyOfficeEditorConfigResponseDto buildEditorConfig(DocumentMetadata document, TaskDocumentConfig config) {
        boolean editable = Boolean.TRUE.equals(document.getEditable()) && !isWorkflowReadOnly(document);
        return OnlyOfficeEditorConfigResponseDto.builder()
                .documentId(document.getId())
                .documentKey(document.getOnlyOfficeDocumentKey())
                .fileName(document.getOriginalName())
                .title(document.getOriginalName())
                .fileType(resolveFileType(document.getOriginalName()))
                .mode(editable ? "edit" : "view")
                .editable(editable)
                .collaborativeEditing(Boolean.TRUE.equals(document.getCollaborativeEditing()))
                .readOnlyAfterComplete(config != null ? config.getReadOnlyAfterComplete() : null)
                .requireApproval(config != null ? config.getRequireApproval() : null)
                .templateDocumentId(document.getTemplateDocumentId())
                .currentEditor(document.getCurrentEditor())
                .editingStartedAt(document.getEditingStartedAt())
                .onlyOfficeReady(false)
                .message("Configuracion preparada. Falta conectar servidor OnlyOffice real en Fase 2.3")
                .build();
    }

    private boolean isWorkflowReadOnly(DocumentMetadata document) {
        return document.getDocumentState() == DocumentLifecycleState.FINAL
                || document.getDocumentState() == DocumentLifecycleState.ARCHIVED
                || (Boolean.TRUE.equals(document.getLocked()) && "workflow".equalsIgnoreCase(nullSafe(document.getLockedBy())));
    }

    private TaskDocumentConfig resolveTaskConfig(DocumentMetadata document) {
        if (isBlank(document.getProcessKey()) || document.getProcessVersion() == null || isBlank(document.getTaskDefinitionKey())) {
            return null;
        }
        return taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        document.getTenantId(),
                        document.getProcessKey(),
                        document.getProcessVersion(),
                        document.getTaskDefinitionKey())
                .orElse(null);
    }

    private DocumentMetadata findDocument(String documentId, String tenantId) {
        if (isBlank(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }
        return documentMetadataRepository.findByIdAndTenantId(documentId.trim(), tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
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

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
    }

    private String resolveFileType(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
