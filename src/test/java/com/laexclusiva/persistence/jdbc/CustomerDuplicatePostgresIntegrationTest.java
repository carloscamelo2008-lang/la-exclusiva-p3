package com.laexclusiva.persistence.jdbc;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.jdbc.CustomerRepositoryJdbc;
import com.laexclusiva.service.CustomerService;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomerDuplicatePostgresIntegrationTest {
    @Test
    void findsAllNormalizedNameMatchesWithoutTreatingSharedAddressAsADuplicate() {
        String url = System.getenv(DatabaseConfig.TEST_URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Configure la base aislada la_exclusiva_test.");
        var provider = new JdbcConnectionProvider(DatabaseConfig.fromTestEnvironment());
        var transactions = new JdbcTransactionManager(provider);
        var service = new CustomerService(new CustomerRepositoryJdbc(provider));
        UUID token = UUID.randomUUID();
        String id = "DUP-" + token;
        String phone = Long.toUnsignedString(token.getMostSignificantBits());
        transactions.inTransaction(() -> {
            try {
                service.registerCustomer(id + "a", "\u00C1na   P\u00E9rez " + token, phone + "1", "Calle");
                service.registerCustomer(id + "b", "ANA PEREZ " + token, phone + "2", "Calle");
                service.registerCustomer(id + "c", "Otra persona " + token, phone + "3", "Calle");
                assertEquals(Set.of(id + "a", id + "b"), service.findSimilarNames(" ana perez " + token)
                        .stream().map(Customer::getId).collect(Collectors.toSet()));
            } finally {
                provider.withConnection(connection -> {
                    try (var statement = connection.prepareStatement("DELETE FROM customer WHERE id IN (?, ?, ?)")) {
                        statement.setString(1, id + "a");
                        statement.setString(2, id + "b");
                        statement.setString(3, id + "c");
                        statement.executeUpdate();
                        return null;
                    }
                });
            }
        });
    }
}
