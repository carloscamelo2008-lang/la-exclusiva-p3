package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laexclusiva.model.InitialCatalog;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryProductRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private InventoryService inventory;

    @BeforeEach
    void setUp() {
        // Se arma el catálogo inicial y un inventario vacío en memoria antes de cada prueba.
        ProductService products = new ProductService(new InMemoryProductRepository());
        products.registerAll(InitialCatalog.create());
        inventory = new InventoryService(products, new InMemoryInventoryRepository(),
                new InMemoryStockMovementRepository());
    }

    @Test
    void addStockStoresCombinedQuantityAndLogsEntry() {
        inventory.addStock("POKER_250", 2, 5, "Stock inicial");
        StockSnapshot s = inventory.getStock("POKER_250");
        assertEquals(2, s.fullBaskets());
        assertEquals(5, s.looseUnits());
        assertEquals(81, s.totalUnits());
        assertEquals(MovementType.ENTRY, inventory.movementsOf("POKER_250").get(0).type());
    }

    @Test
    void p1_p2_unitSaleDiscountsOneUnit() {
        inventory.addStock("COSTENITA_175", 1, 0, "inicial");
        inventory.addStock("POKER_750", 1, 0, "inicial");
        inventory.discountSale(List.of(
                new StockRequest("COSTENITA_175", SalePresentation.UNIT, 1),
                new StockRequest("POKER_750", SalePresentation.UNIT, 1)), "Venta 1");
        assertEquals(37, inventory.availableUnits("COSTENITA_175"));
        assertEquals(15, inventory.availableUnits("POKER_750"));
    }

    @Test
    void p3_halfBasketDiscounts19_15_8() {
        inventory.addStock("POKER_250", 1, 0, "inicial");
        inventory.addStock("COSTENA_BACANA_330", 1, 0, "inicial");
        inventory.addStock("POKER_750", 1, 0, "inicial");
        inventory.discountSale(List.of(
                new StockRequest("POKER_250", SalePresentation.HALF_BASKET, 1),
                new StockRequest("COSTENA_BACANA_330", SalePresentation.HALF_BASKET, 1),
                new StockRequest("POKER_750", SalePresentation.HALF_BASKET, 1)), "Venta");
        assertEquals(38 - 19, inventory.availableUnits("POKER_250"));
        assertEquals(30 - 15, inventory.availableUnits("COSTENA_BACANA_330"));
        assertEquals(16 - 8, inventory.availableUnits("POKER_750"));
    }

    @Test
    void p4_fullBasketDiscounts38_30_16() {
        inventory.addStock("AGUILITA_250", 2, 0, "inicial");
        inventory.addStock("COLA_Y_POLA_LATA_330", 2, 0, "inicial");
        inventory.addStock("POKER_750", 2, 0, "inicial");
        inventory.discountSale(List.of(
                new StockRequest("AGUILITA_250", SalePresentation.FULL_BASKET, 1),
                new StockRequest("COLA_Y_POLA_LATA_330", SalePresentation.FULL_BASKET, 1),
                new StockRequest("POKER_750", SalePresentation.FULL_BASKET, 1)), "Venta");
        assertEquals(38, inventory.availableUnits("AGUILITA_250"));
        assertEquals(30, inventory.availableUnits("COLA_Y_POLA_LATA_330"));
        assertEquals(16, inventory.availableUnits("POKER_750"));
    }

    @Test
    void p5_saleWithSeveralReferencesDiscountsEachOne() {
        inventory.addStock("POKER_250", 2, 0, "inicial");
        inventory.addStock("POKER_750", 1, 0, "inicial");
        inventory.discountSale(List.of(
                new StockRequest("POKER_250", SalePresentation.FULL_BASKET, 1),
                new StockRequest("POKER_250", SalePresentation.UNIT, 3),   // misma referencia repetida
                new StockRequest("POKER_750", SalePresentation.HALF_BASKET, 1)), "Venta mixta");
        assertEquals(76 - 38 - 3, inventory.availableUnits("POKER_250"));
        assertEquals(16 - 8, inventory.availableUnits("POKER_750"));
        assertEquals(2, inventory.allMovements().stream()
                .filter(m -> m.type() == MovementType.EXIT).count());
    }

    @Test
    void p6_insufficientStockRejectsSaleWithoutPartialChanges() {
        inventory.addStock("POKER_250", 1, 0, "inicial");   // 38 unidades
        inventory.addStock("POKER_750", 0, 3, "inicial");   // solo 3 unidades
        List<StockRequest> sale = List.of(
                new StockRequest("POKER_250", SalePresentation.HALF_BASKET, 1),  // alcanza
                new StockRequest("POKER_750", SalePresentation.HALF_BASKET, 1)); // requiere 8, hay 3
        assertFalse(inventory.canFulfill(sale));
        assertThrows(InsufficientStockException.class, () -> inventory.discountSale(sale, "Venta"));
        // Nada cambió, ni siquiera la línea que sí alcanzaba.
        assertEquals(38, inventory.availableUnits("POKER_250"));
        assertEquals(3, inventory.availableUnits("POKER_750"));
        assertTrue(inventory.allMovements().stream().noneMatch(m -> m.type() == MovementType.EXIT));
    }

    @Test
    void inventoryNeverGoesNegative() {
        inventory.addStock("POKER_750", 0, 5, "inicial");
        assertThrows(InsufficientStockException.class, () -> inventory.discountSale(
                List.of(new StockRequest("POKER_750", SalePresentation.UNIT, 6)), "Venta"));
        assertEquals(5, inventory.availableUnits("POKER_750"));
    }

    @Test
    void unknownProductAndEmptySaleAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> inventory.addStock("NO_EXISTE", 1, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> inventory.discountSale(List.of(), "x"));
        assertThrows(IllegalArgumentException.class, () -> inventory.addStock("POKER_750", 0, 0, "x"));
    }

    @Test
    void productWithoutStockRecordHasZeroAvailable() {
        assertEquals(0, inventory.availableUnits("POKER_750"));
        assertEquals(0, inventory.getStock("POKER_750").totalUnits());
    }
}
