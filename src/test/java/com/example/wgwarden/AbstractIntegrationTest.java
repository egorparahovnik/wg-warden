package com.example.wgwarden;

import com.example.wgwarden.config.AuthRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.registration-enabled=true",
        "app.bootstrap-token=test-bootstrap-token"
})
@Testcontainers
@ActiveProfiles("dev")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    AuthRateLimitFilter rateLimit;

    /**
     * MockMvc всегда ходит с одного IP, поэтому окно лимита (10 попыток в минуту) складывалось
     * между тестами и часть проверок получала 429 вместо ожидаемого статуса.
     */
    @BeforeEach
    void resetRateLimit() {
        rateLimit.clear();
    }
}
