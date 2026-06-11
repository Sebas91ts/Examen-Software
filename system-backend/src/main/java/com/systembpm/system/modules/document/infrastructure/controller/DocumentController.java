package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.DocumentDownloadUrlResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponseDto>> upload(
            @Valid @ModelAttribute DocumentUploadRequestDto request,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        String uploadedBy = authentication != null ? authentication.getName() : "anonymous";
        log.info("Solicitud POST /api/documents/upload para tenant {} y proceso {}", request.getTenantId(), request.getProcessInstanceId());

        DocumentUploadResponseDto response = documentService.upload(request, file, uploadedBy);
        return ResponseEntity.ok(ApiResponse.success("Documento subido exitosamente", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentMetadataResponseDto>> getById(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Metadata documental obtenida", documentService.getById(id, requester)));
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<ApiResponse<DocumentDownloadUrlResponseDto>> getDownloadUrl(
            @PathVariable("id") String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("URL temporal generada", documentService.getDownloadUrl(id, requester)));
    }

    @GetMapping("/process/{processInstanceId}")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponseDto>>> getByProcessInstanceId(
            @PathVariable("processInstanceId") String processInstanceId,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Documentos del proceso obtenidos",
                documentService.getByProcessInstanceId(processInstanceId, requester)
        ));
    }
}
