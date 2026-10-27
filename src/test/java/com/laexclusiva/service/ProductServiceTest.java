package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.repository.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(new InMemoryProductRepository());
        service.registerAll(InitialCatalog.create());
    }

    @Test
    void sixReferencesAreRegisteredAndQueryable() {
        assertEquals(6, service.listAll().size());
        assertEquals("Poker", service.findById("POKER_750").getName());
    }

    @Test
    void duplicateIdIsRejected() {
        BeerProduct dup = InitialCatalog.create().get(0);
        assertThrows(IllegalArgumentException.class, () -> service.register(dup));
    }

    @Test
    void productWithoutAllPricesIsRejected() {
        BeerProduct incomplete = new BeerProduct("NEW_1", "Nueva", 330, 30,
                ContainerType.NON_RETURNABLE_CAN, null);
        incomplete.definePrice(SalePresentation.UNIT, 2_500);
        assertThrows(IllegalArgumentException.class, () -> service.register(incomplete));
    }

    @Test
    void unknownProductThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.findById("NO_EXISTE"));
    }

    @Test
    void priceAndUnitsLookup() {
        assertEquals(90_000, service.priceFor("POKER_750", SalePresentation.FULL_BASKET));
        assertEquals(15, service.unitsFor("COLA_Y_POLA_LATA_330", SalePresentation.HALF_BASKET));
    }
}
