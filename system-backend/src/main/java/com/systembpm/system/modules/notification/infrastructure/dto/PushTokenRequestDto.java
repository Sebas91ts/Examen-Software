package com.systembpm.system.modules.notification.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenRequestDto {

    @NotBlank(message = "El token push es obligatorio")
    private String token;
}
