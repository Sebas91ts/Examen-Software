package com.systembpm.system.modules.client.application.service;

import com.systembpm.system.modules.client.application.dto.ClientTaskCompleteResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskFormResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskListItemDto;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

public interface ClientTaskService {
    List<ClientTaskListItemDto> listarTareasCliente(String clientEmail);

    ClientTaskFormResponseDto obtenerFormularioTarea(String taskId, String clientEmail);

    ClientTaskCompleteResponseDto completarTarea(
            String taskId,
            String clientEmail,
            Map<String, Object> formData,
            MultiValueMap<String, MultipartFile> files);
}
