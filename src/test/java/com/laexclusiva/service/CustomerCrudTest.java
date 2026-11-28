package com.laexclusiva.service;

import com.laexclusiva.model.CustomerDeletionBlockedException;
import com.laexclusiva.repository.CustomerRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CustomerCrudTest {
    private final CustomerService service = new CustomerService(new CustomerRepository("historial"::equals));

    @Test
    void updatesContactWithoutChangingBalanceAndAllowsSharedAddress() {
        service.registerCustomer("a", "Ana", " +57 300123 ", "Calle 1");
        service.registerCustomer("b", "Beto", "0045", "Calle 1");
        service.addDebtToCustomer("a", BigDecimal.TEN);
        service.updateCustomer("a", "Ana María", "57300 123", "Calle 1");
        var updated = service.getCustomerById("a");
        assertEquals("Ana María", updated.getName());
        assertEquals("57300123", updated.getPhone());
        assertEquals("0045", service.getCustomerById("b").getPhone());
        assertEquals("Calle 1", updated.getAddress());
        assertEquals(BigDecimal.TEN, updated.getBalance());
        assertThrows(IllegalArgumentException.class, () -> service.updateCustomer("missing", "Ana", "1", "Calle"));
    }

    @Test
    void rejectsNormalizedDuplicateOnCreateAndEdit() {
        service.registerCustomer("a", "Ana", "300 12", "Calle 1");
        service.registerCustomer("b", "Beto", "30013", "Calle 1");
        assertThrows(IllegalArgumentException.class, () -> service.registerCustomer("c", "C", "+30012", "Calle 1"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCustomer("b", "Beto", "+30012", "Calle 1"));
        assertEquals("30013", service.getCustomerById("b").getPhone());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-300", "30-0", "abc", "++300", "300.1", "+"})
    void rejectsInvalidPhoneOnCreateAndEdit(String phone) {
        service.registerCustomer("a", "Ana", "30012", "Calle 1");
        assertThrows(IllegalArgumentException.class, () -> service.registerCustomer("b", "Beto", phone, "Calle 1"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCustomer("a", "Ana", phone, "Calle 1"));
        assertEquals("30012", service.getCustomerById("a").getPhone());
    }

    @Test
    void normalizesContactAndDetectsNamesWithoutDiacritics() {
        service.registerCustomer("  C  001 ", "  María   Pérez ", "30012", "  Calle   1 ",
                new BigDecimal("2500.00"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerCustomer("C 001", "Otro", "30013", "Calle 1"));
        assertEquals("C 001", service.getCustomerById(" C  001 ").getId());
        assertEquals("María Pérez", service.getCustomerById("C 001").getName());
        assertEquals("Calle 1", service.getCustomerById("C 001").getAddress());
        assertEquals(1, service.findSimilarNames(" MARIA  perez ").size());
        assertTrue(service.findSimilarNames("María Gómez").isEmpty());
        service.updateCustomer(" C 001 ", "María Pérez", "30012", " Calle  2 ");
        assertEquals(BigDecimal.ZERO, service.getCustomerById("C 001").getBalance());
        assertDoesNotThrow(() -> service.updateCustomer("C 001", "María", "30012", "Calle",
                BigDecimal.ONE.negate()), "El argumento legado de cupo ya no gobierna la actualización.");
    }

    @Test
    void createsCustomerWithGeneratedStableFormatIdentifier() {
        var created = service.registerCustomer("Ana", "300123", "Calle 1");

        assertTrue(created.getId().matches("CLI-[0-9A-F-]{36}"));
        assertEquals(created.getId(), service.getCustomerById(created.getId()).getId());
        assertEquals("Ana", created.getName());
        assertEquals(BigDecimal.ZERO, created.getBalance());
    }

    @Test
    void deletesOnlyWhenThereIsNeitherBalanceNorHistory() {
        service.registerCustomer("a", "Ana", "1", "Calle 1");
        service.registerCustomer("b", "Beto", "2", "Calle 1");
        service.registerCustomer("historial", "C", "3", "Calle 1");
        service.addDebtToCustomer("b", BigDecimal.ONE);

        service.deleteCustomer("a");
        assertThrows(IllegalArgumentException.class, () -> service.getCustomerById("a"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteCustomer("a"));
        assertThrows(CustomerDeletionBlockedException.class, () -> service.deleteCustomer("b"));
        assertThrows(CustomerDeletionBlockedException.class, () -> service.deleteCustomer("historial"));
    }
}
