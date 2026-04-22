package com.systembpm.system.modules.form.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "form_definitions")
@CompoundIndex(name = "form_unique_index", def = "{'processKey': 1, 'processVersion': 1, 'taskDefinitionKey': 1}", unique = true)
public class FormDefinition {

    @Id
    private String id;

    private String processKey;

    private Integer processVersion;

    private String taskDefinitionKey;

    private String title;

    private List<FormFieldDefinition> fields;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
