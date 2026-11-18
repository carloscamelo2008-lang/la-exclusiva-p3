package com.laexclusiva.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationServicesTest {

    @Test
    void keepsExistingMemoryCompositionAsAnExplicitSupportedMode() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());

        assertFalse(services.postgresMode());
        assertFalse(services.sales().supportsDurableIdempotency());
        assertNull(services.debtQuery());
        assertEquals(6, services.products().listAll().size());
    }

    @Test
    void rejectsUnknownPersistenceModesBeforeOpeningAnyConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> ApplicationServices.create("mysql", Map.of()));
    }
}
