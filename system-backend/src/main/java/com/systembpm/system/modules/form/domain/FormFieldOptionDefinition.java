package com.systembpm.system.modules.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldOptionDefinition {
    private String label;
    private String value;
}
