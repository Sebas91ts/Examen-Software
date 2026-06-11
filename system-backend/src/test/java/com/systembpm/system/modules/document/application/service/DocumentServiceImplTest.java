package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentUploadRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentSizeExceededException;
import com.systembpm.system.modules.document.domain.DocumentStatus;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.infrastructure.config.DocumentProperties;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentMetadataRepository documentMetadataRepository;

    @Mock
    private DocumentStoragePort documentStoragePort;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DocumentProperties documentProperties;

    @Mock
    private DocumentProperties.Storage storage;

    @Mock
    private DocumentProperties.S3 s3;

    @Mock
    private DocumentProperties.SignedUrl signedUrl;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private Usuario requester;

    @BeforeEach
    void setUp() {
        requester = Usuario.builder()
                .email("test@test.com")
                .tenantId("tenant-a")
                .activo(true)
                .build();

        lenient().when(usuarioRepository.findByEmail("test@test.com")).thenReturn(Optional.of(requester));
        lenient().when(documentProperties.maxFileSizeBytes()).thenReturn(10_485_760L);
        lenient().when(documentProperties.allowedContentTypes()).thenReturn(List.of("application/pdf", "image/*"));
        lenient().when(documentProperties.signedUrl()).thenReturn(signedUrl);
        lenient().when(signedUrl.expirationMinutes()).thenReturn(15L);
        lenient().when(documentProperties.storage()).thenReturn(storage);
        lenient().when(storage.s3()).thenReturn(s3);
    }

    @Test
    void uploadShouldPersistAuditFieldsAndMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "hello".getBytes()
        );
        DocumentUploadRequestDto request = DocumentUploadRequestDto.builder()
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .build();

        when(documentMetadataRepository.findTopByTenantIdAndProcessInstanceIdAndOriginalNameOrderByVersionDesc(
                "tenant-a", "proc-123", "contract.pdf"
        )).thenReturn(Optional.empty());
        when(documentMetadataRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentUploadResponseDto response = documentService.upload(request, file, "test@test.com");

        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(documentMetadataRepository).save(captor.capture());
        DocumentMetadata saved = captor.getValue();

        assertNotNull(saved.getUploadedAt());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("test@test.com", saved.getUploadedBy());
        assertEquals("test@test.com", saved.getUpdatedBy());
        assertEquals(1, saved.getVersion());
        assertEquals(DocumentStatus.ACTIVE, saved.getStatus());
        assertEquals(saved.getUploadedAt(), response.getUploadedAt());
        assertEquals("tenant-a", response.getTenantId());
    }

    @Test
    void uploadShouldIncreaseVersionWhenPreviousDocumentExists() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "hello".getBytes()
        );
        DocumentUploadRequestDto request = DocumentUploadRequestDto.builder()
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .build();

        when(documentMetadataRepository.findTopByTenantIdAndProcessInstanceIdAndOriginalNameOrderByVersionDesc(
                "tenant-a", "proc-123", "contract.pdf"
        )).thenReturn(Optional.of(DocumentMetadata.builder().version(3).build()));
        when(documentMetadataRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentUploadResponseDto response = documentService.upload(request, file, "test@test.com");

        assertEquals(4, response.getVersion());
        assertTrue(response.getFileName().contains("-v4"));
    }

    @Test
    void uploadShouldRejectWhenFileSizeExceedsLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                new byte[12]
        );
        DocumentUploadRequestDto request = DocumentUploadRequestDto.builder()
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .build();

        when(documentProperties.maxFileSizeBytes()).thenReturn(10L);

        assertThrows(DocumentSizeExceededException.class, () -> documentService.upload(request, file, "test@test.com"));
        verify(documentStoragePort, never()).upload(anyString(), anyString(), anyLong(), any(InputStream.class));
    }

    @Test
    void uploadShouldRejectWhenTenantDoesNotMatchRequester() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "hello".getBytes()
        );
        DocumentUploadRequestDto request = DocumentUploadRequestDto.builder()
                .tenantId("tenant-b")
                .processInstanceId("proc-123")
                .build();

        assertThrows(DocumentTenantAccessDeniedException.class, () -> documentService.upload(request, file, "test@test.com"));
    }

    @Test
    void uploadShouldRejectUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "hello".getBytes()
        );
        DocumentUploadRequestDto request = DocumentUploadRequestDto.builder()
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .build();

        assertThrows(DocumentValidationException.class, () -> documentService.upload(request, file, "test@test.com"));
    }

    @Test
    void getDownloadUrlShouldUpdateLastAccessedAt() {
        Instant createdAt = Instant.now();
        DocumentMetadata metadata = DocumentMetadata.builder()
                .id("doc-1")
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .originalName("contract.pdf")
                .fileName("doc-1-v1.pdf")
                .mimeType("application/pdf")
                .size(10L)
                .s3Key("tenant-a/proc-123/doc-1/1/doc-1-v1.pdf")
                .uploadedBy("test@test.com")
                .uploadedAt(createdAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .version(1)
                .status(DocumentStatus.ACTIVE)
                .build();

        when(documentMetadataRepository.findById("doc-1")).thenReturn(Optional.of(metadata));
        when(documentMetadataRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentStoragePort.generateDownloadUrl(anyString(), anyString(), any()))
                .thenReturn(new PresignedDownloadUrl("https://signed.example/doc-1", Instant.now().plusSeconds(900)));

        var response = documentService.getDownloadUrl("doc-1", "test@test.com");

        assertNotNull(response.getExpiresAt());
        verify(documentMetadataRepository).save(any(DocumentMetadata.class));
    }

    @Test
    void getByIdShouldUpdateLastAccessedAt() {
        Instant createdAt = Instant.now();
        DocumentMetadata metadata = DocumentMetadata.builder()
                .id("doc-1")
                .tenantId("tenant-a")
                .processInstanceId("proc-123")
                .originalName("contract.pdf")
                .uploadedAt(createdAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .version(1)
                .status(DocumentStatus.ACTIVE)
                .build();

        when(documentMetadataRepository.findById("doc-1")).thenReturn(Optional.of(metadata));
        when(documentMetadataRepository.save(any(DocumentMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = documentService.getById("doc-1", "test@test.com");

        assertNotNull(response.getLastAccessedAt());
        verify(documentMetadataRepository).save(any(DocumentMetadata.class));
    }

    @Test
    void getDownloadUrlShouldRejectCrossTenantAccess() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .id("doc-1")
                .tenantId("tenant-b")
                .processInstanceId("proc-123")
                .originalName("contract.pdf")
                .version(1)
                .status(DocumentStatus.ACTIVE)
                .build();

        when(documentMetadataRepository.findById("doc-1")).thenReturn(Optional.of(metadata));

        assertThrows(DocumentTenantAccessDeniedException.class, () -> documentService.getDownloadUrl("doc-1", "test@test.com"));
    }
}
