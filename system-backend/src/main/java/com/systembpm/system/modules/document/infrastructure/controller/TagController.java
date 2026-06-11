package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.TagCreateRequestDto;
import com.systembpm.system.modules.document.application.dto.TagResponseDto;
import com.systembpm.system.modules.document.application.dto.TagUpdateRequestDto;
import com.systembpm.system.modules.document.application.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponseDto>> create(
            @Valid @RequestBody TagCreateRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tag creado", tagService.create(request, requester)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponseDto>>> list(Authentication authentication) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tags obtenidos", tagService.list(requester)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponseDto>> getById(
            @PathVariable String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tag obtenido", tagService.getById(id, requester)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponseDto>> update(
            @PathVariable String id,
            @Valid @RequestBody TagUpdateRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Tag actualizado", tagService.update(id, request, requester)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        tagService.delete(id, requester);
        return ResponseEntity.ok(ApiResponse.success("Tag eliminado", null));
    }
}
