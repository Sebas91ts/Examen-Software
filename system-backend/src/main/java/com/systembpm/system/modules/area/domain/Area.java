package com.systembpm.system.modules.area.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "areas")
public class Area {

    @Id
    private String id;

    @Indexed(unique = true)
    private String nombre;

    private String descripcion;

    private Boolean activa;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
