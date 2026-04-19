package com.systembpm.system.modules.process.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entidad Mongo para almacenar procesos BPMN serializados como XML.
 */
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

    private LocalDateTime createdAt;
}
