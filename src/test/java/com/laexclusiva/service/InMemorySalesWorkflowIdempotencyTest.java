package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemorySalesWorkflowIdempotencyTest {
    @Test
    void saleRetryWithSameKeyDoesNotRepeatInMemoryEffects() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        Seller seller = services.sellers().registerSeller("Vendedor ficticio", "3000000001", "Dirección ficticia");
        BeerProduct product = services.products().listAll().get(0);
        services.inventory().addStock(product.getId(), 0, 2, "Fixture unitario");
        List<StockRequest> request = List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 1));
        String key = "memory-sale-key";

        Sale first = services.sales().registerSale(request, seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key);
        Sale replay = services.sales().registerSale(request, seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key);

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, services.sales().listAll().size());
        assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
        assertThrows(IllegalArgumentException.class, () -> services.sales().registerSale(
                List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 2)), seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key));
        assertEquals(1, services.sales().listAll().size());
    }
}
