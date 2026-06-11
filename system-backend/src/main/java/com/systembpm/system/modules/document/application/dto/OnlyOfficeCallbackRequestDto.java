package com.systembpm.system.modules.document.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeCallbackRequestDto {
    private String key;
    private Integer status;
    private String url;
    private String token;
    private List<String> users;
    private List<OnlyOfficeActionDto> actions;
}
