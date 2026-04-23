package com.systembpm.system.modules.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldDefinition {
    private String name;
    private String label;
    private String type;
    private Boolean required;
    private String placeholder;
    private String helpText;
    private Integer order;
    private List<String> options;
    private List<FormFieldOptionDefinition> optionItems;
}
