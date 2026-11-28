package com.laexclusiva.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class PostgresStartupVerifierIntegrationTest {
    @Test
    void acceptsCommercialSchemaInstalledThroughV112() {
        assumeTrue(Boolean.getBoolean("laexclusiva.test.postgres.commercialDeployment"),
                "Esta verificación requiere una base aislada preparada con el instalador comercial.");
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Configure la base aislada la_exclusiva_test.");
        var provider = new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
        assertDoesNotThrow(new PostgresStartupVerifier(provider)::verify);
    }
}
