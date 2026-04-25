package com.systembpm.system.modules.dashboard.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricItemDto {

    private String label;
    private Long total;
}
