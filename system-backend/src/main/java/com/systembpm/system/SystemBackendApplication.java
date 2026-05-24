package com.systembpm.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableMongoAuditing
@ConfigurationPropertiesScan
public class SystemBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SystemBackendApplication.class, args);
	}

}
