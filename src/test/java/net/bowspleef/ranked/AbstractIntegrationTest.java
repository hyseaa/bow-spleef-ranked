package net.bowspleef.ranked;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base of the integration tests. One PostgreSQL container for the whole test run: Spring caches
 * application contexts between test classes, and a cached context keeps the datasource of the
 * container it was built with, so a container per class would leave later classes talking to a
 * stopped one ("Failed to obtain JDBC Connection"). The container is stopped at the end of the JVM.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
