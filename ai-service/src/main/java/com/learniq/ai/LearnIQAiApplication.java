package com.learniq.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;

// DataSource/JdbcTemplate auto-config is required by the RAG pipeline
// (TenantSchemaService, RagDocumentRepository, IngestionService, AdminChatToolService).
// JPA, Spring Data JPA and Flyway stay excluded: ai-service uses raw JDBC and never owns migrations.
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
public class LearnIQAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnIQAiApplication.class, args);
    }
}
