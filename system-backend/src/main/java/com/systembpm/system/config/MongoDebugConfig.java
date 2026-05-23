package com.systembpm.system.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoDebugConfig {

    private final MongoTemplate mongoTemplate;

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017}")
    private String mongodbUri;

    @Value("${spring.data.mongodb.database:systembpm_db}")
    private String mongodbDatabase;

    @Bean
    public CommandLineRunner logMongoConnectionInfo() {
        return args -> {
            try {
                String resolvedDatabase = mongoTemplate.getDb().getName();
                log.info("MongoDB configurado -> uri={}, databaseConfigurada={}, databaseActiva={}",
                        sanitizeUri(mongodbUri), mongodbDatabase, resolvedDatabase);
            } catch (Exception ex) {
                log.warn("No se pudo resolver la base de datos activa de MongoDB", ex);
            }
        };
    }

    private String sanitizeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }

        int atIndex = uri.indexOf('@');
        if (atIndex > 0) {
            int schemeIndex = uri.indexOf("://");
            if (schemeIndex > 0 && schemeIndex < atIndex) {
                return uri.substring(0, schemeIndex + 3) + "***:***@" + uri.substring(atIndex + 1);
            }
        }

        return uri;
    }
}
