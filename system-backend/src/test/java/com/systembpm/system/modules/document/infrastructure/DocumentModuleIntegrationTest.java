package com.systembpm.system.modules.document.infrastructure;

import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
class DocumentModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private DocumentMetadataRepository documentMetadataRepository;

    @MockBean
    private DocumentStoragePort documentStoragePort;

    @BeforeEach
    void setUp() {
        documentMetadataRepository.deleteAll();
        usuarioRepository.deleteAll();
        doNothing().when(documentStoragePort).upload(anyString(), anyString(), anyLong(), any(InputStream.class));
    }

    @Test
    @WithMockUser(username = "user-a@test.com", roles = {"USER"})
    void uploadShouldRejectTenantMismatchWith403() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .email("user-a@test.com")
                .tenantId("tenant-a")
                .activo(true)
                .roles(java.util.List.of("ROLE_USER"))
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("tenantId", "tenant-b")
                        .param("processInstanceId", "proc-123")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user-a@test.com", roles = {"USER"})
    void uploadShouldPersistUploadedAt() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .email("user-a@test.com")
                .tenantId("tenant-a")
                .activo(true)
                .roles(java.util.List.of("ROLE_USER"))
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("tenantId", "tenant-a")
                        .param("processInstanceId", "proc-123")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.uploadedAt", notNullValue()))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt", notNullValue()));
    }

    @Test
    @WithMockUser(username = "user-a@test.com", roles = {"USER"})
    void listByProcessShouldReturnEmptyArrayWhenNone() throws Exception {
        usuarioRepository.save(Usuario.builder()
                .email("user-a@test.com")
                .tenantId("tenant-a")
                .activo(true)
                .roles(java.util.List.of("ROLE_USER"))
                .build());

        mockMvc.perform(get("/api/documents/process/proc-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
