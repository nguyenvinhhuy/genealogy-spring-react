package com.genealogy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Loads the full application context against a real Postgres, so Flyway and JPA validation both run. */
@Testcontainers
@SpringBootTest
class GenealogyApplicationTests {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    /**
     * Points the datasource at the throwaway container.
     *
     * @param registry the property registry to populate
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-secret-value-at-least-32-bytes-long");
        // The MinIO client refuses empty credentials, and CI has none in its environment.
        registry.add("app.storage.minio.access-key", () -> "test-access-key");
        registry.add("app.storage.minio.secret-key", () -> "test-secret-key");
    }

    @Test
    void contextLoads() {
    }
}
