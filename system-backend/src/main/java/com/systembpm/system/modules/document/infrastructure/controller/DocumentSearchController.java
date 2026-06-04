package com.systembpm.system.modules.document.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.document.application.dto.DocumentSearchRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentSearchResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentSearchController {

    private final DocumentSearchService documentSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<DocumentSearchResponseDto>> search(
            @ModelAttribute DocumentSearchRequestDto request,
            Authentication authentication
    ) {
        String requester = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success("Busqueda documental ejecutada", documentSearchService.search(request, requester)));
    }
}
