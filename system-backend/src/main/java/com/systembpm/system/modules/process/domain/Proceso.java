package com.systembpm.system.modules.process.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "procesos")
public class Proceso {

    @Id
    private String id;

    private String nombre;
    private String xml;
    private Integer version;
    private String estado; 
    private String createdBy;
    private String processKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
