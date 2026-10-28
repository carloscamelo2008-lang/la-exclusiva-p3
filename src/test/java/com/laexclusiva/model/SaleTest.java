package com.laexclusiva.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 27, 10, 30);

    private BeerProduct product(String id) {
        return InitialCatalog.create().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private Seller seller() {
        return new Seller("Vendedor", "3001234567", "Calle 1");
    }

    private Customer customer() {
        return new Customer("C001", "Cliente", "3007654321", "Calle 2");
    }

    private Sale sale(Customer customer, List<SaleLine> lines, BigDecimal initialPayment) {
        return new Sale(CREATED_AT, seller(), customer, lines, initialPayment);
    }

    @Test
    void calculatesLineSubtotalsAndMixedSaleTotalFromConfiguredPresentationPrices() {
        SaleLine fullBasket = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);
        SaleLine halfBaskets = new SaleLine(product("POKER_750"), SalePresentation.HALF_BASKET, 2);
        SaleLine units = new SaleLine(product("POKER_250"), SalePresentation.UNIT, 3);

        Sale sale = sale(customer(), List.of(fullBasket, halfBaskets, units), BigDecimal.ZERO);

        assertEquals(BigDecimal.valueOf(85_000L), fullBasket.getSubtotal());
        assertEquals(BigDecimal.valueOf(90_000L), halfBaskets.getSubtotal());
        assertEquals(BigDecimal.valueOf(7_500L), units.getSubtotal());
        assertEquals(BigDecimal.valueOf(182_500L), sale.calculateTotal());
    }

    @Test
    void saleLineKeepsTheAppliedPriceWhenCatalogPriceChanges() {
        BeerProduct product = product("COSTENITA_175");
        SaleLine line = new SaleLine(product, SalePresentation.FULL_BASKET, 1);

        product.definePrice(SalePresentation.FULL_BASKET, BigDecimal.valueOf(100_000L));

        assertEquals(BigDecimal.valueOf(85_000L), line.getUnitPrice());
        assertEquals(BigDecimal.valueOf(85_000L), line.getSubtotal());
        assertEquals(BigDecimal.valueOf(100_000L), product.priceFor(SalePresentation.FULL_BASKET));
    }

    @Test
    void rejectsInvalidSaleLineDataAndProductsWithoutConfiguredPrice() {
        BeerProduct productWithoutPrices = new BeerProduct(
                "NO_PRICE", "Sin precio", 330, 30, ContainerType.NON_RETURNABLE_CAN, null);

        assertThrows(IllegalArgumentException.class,
                () -> new SaleLine(null, SalePresentation.UNIT, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleLine(product("POKER_250"), null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleLine(product("POKER_250"), SalePresentation.UNIT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleLine(product("POKER_250"), SalePresentation.UNIT, -1));
        assertThrows(IllegalStateException.class,
                () -> new SaleLine(productWithoutPrices, SalePresentation.UNIT, 1));
    }

    @Test
    void acceptsFullyPaidSaleWithoutCustomerAndRequiresCustomerForPendingBalance() {
        SaleLine line = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);
        Sale fullyPaid = sale(null, List.of(line), BigDecimal.valueOf(85_000L));

        assertNotNull(fullyPaid.getId());
        assertEquals(BigDecimal.valueOf(85_000L), fullyPaid.calculateTotalPaid());
        assertEquals(BigDecimal.ZERO, fullyPaid.calculateBalance());
        assertEquals(1, fullyPaid.getPayments().size());

        Sale withBalance = sale(customer(), List.of(line), BigDecimal.valueOf(20_000L));
        assertEquals(BigDecimal.valueOf(65_000L), withBalance.calculateBalance());
        assertNotNull(withBalance.getCustomer());

        assertThrows(IllegalArgumentException.class,
                () -> sale(null, List.of(line), BigDecimal.ZERO));
    }

    @Test
    void rejectsMissingRequiredSaleDataAndInvalidInitialPayments() {
        SaleLine line = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new Sale(null, seller(), customer(), List.of(line), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Sale(CREATED_AT, null, customer(), List.of(line), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Sale(CREATED_AT, seller(), customer(), null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Sale(CREATED_AT, seller(), customer(), List.of(), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Sale(CREATED_AT, seller(), customer(), java.util.Arrays.asList(line, null), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> sale(customer(), List.of(line), null));
        assertThrows(IllegalArgumentException.class,
                () -> sale(customer(), List.of(line), BigDecimal.valueOf(-1L)));
        assertThrows(IllegalArgumentException.class,
                () -> sale(customer(), List.of(line), BigDecimal.valueOf(85_001L)));
        assertThrows(IllegalArgumentException.class,
                () -> sale(new Customer("  ", "Cliente", "3007654321", "Calle 2"),
                        List.of(line), BigDecimal.ZERO));
    }

    @Test
    void rejectsNullZeroNegativeAndUndatedSalePayments() {
        assertThrows(IllegalArgumentException.class,
                () -> new SalePayment(null, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new SalePayment(BigDecimal.ZERO, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new SalePayment(BigDecimal.valueOf(-1L), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new SalePayment(BigDecimal.ONE, null));
    }

    @Test
    void rejectsInitialPaymentOverTotalAndLaterPaymentOverBalance() {
        SaleLine line = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);
        assertThrows(IllegalArgumentException.class,
                () -> sale(customer(), List.of(line), BigDecimal.valueOf(85_001L)));

        Sale sale = sale(customer(), List.of(line), BigDecimal.valueOf(20_000L));
        assertThrows(IllegalArgumentException.class,
                () -> sale.registerPayment(new SalePayment(BigDecimal.valueOf(65_001L), CREATED_AT.plusDays(1))));
        assertEquals(BigDecimal.valueOf(65_000L), sale.calculateBalance());
    }

    @Test
    void updatesPaidAmountAndBalanceAfterSeveralPayments() {
        SaleLine line = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);
        Sale sale = sale(customer(), List.of(line), BigDecimal.valueOf(5_000L));

        sale.registerPayment(new SalePayment(BigDecimal.valueOf(10_000L), CREATED_AT.plusDays(1)));
        sale.registerPayment(new SalePayment(BigDecimal.valueOf(20_000L), CREATED_AT.plusDays(2)));

        assertEquals(BigDecimal.valueOf(35_000L), sale.calculateTotalPaid());
        assertEquals(BigDecimal.valueOf(50_000L), sale.calculateBalance());
        assertEquals(3, sale.getPayments().size());
        assertEquals(CREATED_AT, sale.getPayments().get(0).getTimestamp());

        sale.registerPayment(new SalePayment(BigDecimal.valueOf(50_000L), CREATED_AT.plusDays(3)));
        assertEquals(BigDecimal.valueOf(85_000L), sale.calculateTotalPaid());
        assertEquals(BigDecimal.ZERO, sale.calculateBalance());
        assertThrows(IllegalArgumentException.class,
                () -> sale.registerPayment(new SalePayment(BigDecimal.ONE, CREATED_AT.plusDays(4))));
        assertEquals(BigDecimal.ZERO, sale.calculateBalance());
    }

    @Test
    void rejectedPaymentDoesNotChangeFinancialStateOrExposedCollections() {
        SaleLine line = new SaleLine(product("COSTENITA_175"), SalePresentation.FULL_BASKET, 1);
        Seller assignedSeller = seller();
        List<SaleLine> inputLines = new ArrayList<>(List.of(line));
        Sale sale = new Sale(CREATED_AT, assignedSeller, customer(), inputLines, BigDecimal.valueOf(10_000L));
        inputLines.clear();
        List<SalePayment> paymentSnapshot = sale.getPayments();

        assertThrows(IllegalArgumentException.class,
                () -> sale.registerPayment(new SalePayment(BigDecimal.valueOf(75_001L), CREATED_AT.plusDays(1))));

        assertEquals(BigDecimal.valueOf(10_000L), sale.calculateTotalPaid());
        assertEquals(BigDecimal.valueOf(75_000L), sale.calculateBalance());
        assertEquals(1, sale.getPayments().size());
        assertEquals(1, paymentSnapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> sale.getPayments().add(new SalePayment(BigDecimal.ONE, CREATED_AT.plusDays(1))));
        assertEquals(1, sale.getLines().size());
        assertThrows(UnsupportedOperationException.class, () -> sale.getLines().clear());
        assertNotSame(assignedSeller, sale.getSeller());
        assertEquals(assignedSeller.getId(), sale.getSeller().getId());
        assertTrue(sale.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO) > 0);
    }
}
