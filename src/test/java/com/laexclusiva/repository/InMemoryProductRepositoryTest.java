package com.laexclusiva.repository;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.ContainerType;
import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.SalePresentation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryProductRepositoryTest {
    private final ProductRepository repository = new InMemoryProductRepository();

    @Test
    void saveRequiresAllPricesAndRejectsDuplicateIds() {
        BeerProduct incomplete = new BeerProduct("NEW", "Nueva", 330, 30,
                ContainerType.NON_RETURNABLE_CAN, null);
        assertThrows(IllegalArgumentException.class, () -> repository.save(incomplete));

        BeerProduct product = InitialCatalog.create().get(0);
        repository.save(product);
        assertThrows(IllegalArgumentException.class, () -> repository.save(product));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void priceChangesRequireExplicitOperationsAndExactAmounts() {
        BeerProduct product = InitialCatalog.create().get(0);
        String id = product.getId();
        SalePresentation unit = SalePresentation.UNIT;
        BigDecimal initial = product.priceFor(unit);
        repository.save(product);

        product.definePrice(unit, BigDecimal.TEN);
        repository.findById(id).orElseThrow().definePrice(unit, BigDecimal.ONE);
        assertEquals(initial, repository.findById(id).orElseThrow().priceFor(unit));

        assertThrows(IllegalArgumentException.class,
                () -> repository.addMissingPrice(id, unit, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePrice(id, unit, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePrice(id, unit, new BigDecimal("100000000000000000.00")));
        assertEquals(initial, repository.findById(id).orElseThrow().priceFor(unit));

        BigDecimal maximum = new BigDecimal("99999999999999999.99");
        repository.updatePrice(id, unit, maximum);
        assertEquals(maximum, repository.findById(id).orElseThrow().priceFor(unit));
    }
}
