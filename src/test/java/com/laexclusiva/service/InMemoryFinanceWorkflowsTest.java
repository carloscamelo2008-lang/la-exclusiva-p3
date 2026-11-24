package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.CashDirection;
import com.laexclusiva.model.CustomerDebtBreakdown;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.ui.ApplicationServices;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryFinanceWorkflowsTest {
    @Test
    void generalDebtAndCashRemainSeparateAndAvailableInMemoryMode() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        services.customers().registerCustomer("C-MEM-1", "Cliente memoria", "3000000100", "Dirección ficticia", new BigDecimal("10000000"));
        Seller seller = services.sellers().registerSeller("Vendedor memoria", "3000000101", "Dirección ficticia");
        var product = services.products().listAll().get(0);
        BigDecimal saleTotal = product.priceFor(SalePresentation.UNIT);
        BigDecimal initialPayment = saleTotal.divide(new BigDecimal("2.00")).setScale(2);
        services.inventory().addStock(product.getId(), 0, 2, "Fixture financiero en memoria");

        services.generalDebt().registerDebt("C-MEM-1", new BigDecimal("30.00"), "Ajuste",
                "Deuda general inicial", "general-debt-1");
        var identityFixture = new com.laexclusiva.testutil.IdentityTestFixture();
        var testAuthorization = identityFixture.authorization();
        var testAdmin = identityFixture.issue(com.laexclusiva.model.UserRole.ADMINISTRATOR).session();
        Sale sale = testAuthorization.execute(testAdmin, com.laexclusiva.model.Permission.REGISTER_SALE, () -> services.sales().registerSale(
                List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 1)), seller.getId(),
                "C-MEM-1", initialPayment, "sale-memory-1"));
        CustomerDebtBreakdown beforePayment = services.generalDebt().findBalance("C-MEM-1");
        assertEquals(sale.calculateBalance(), beforePayment.outstandingSalesDebt());
        assertEquals(new BigDecimal("30.00"), beforePayment.generalDebt());

        services.generalDebt().registerPayment("C-MEM-1", new BigDecimal("20.00"), "Abono general",
                "Pago independiente de ventas", "general-payment-1");
        services.generalDebt().registerPayment("C-MEM-1", new BigDecimal("20.00"), "Abono general",
                "Pago independiente de ventas", "general-payment-1");
        CustomerDebtBreakdown afterPayment = services.generalDebt().findBalance("C-MEM-1");
        assertEquals(new BigDecimal("10.00"), afterPayment.generalDebt());
        assertEquals(sale.calculateBalance(), afterPayment.outstandingSalesDebt());
        assertEquals(1, services.generalDebt().listMovements("C-MEM-1", null, null, null).stream()
                .filter(row -> row.type().name().equals("PAYMENT")).count());
        assertThrows(IllegalArgumentException.class, () -> services.generalDebt().registerPayment(
                "C-MEM-1", new BigDecimal("11.00"), "Abono general", "Supera deuda", "too-much"));

        LocalDate today = LocalDate.now();
        services.cash().registerMovement(CashDirection.INCOME, CashCategory.OTHER_INCOME,
                new BigDecimal("25.00"), LocalDateTime.now(), "Aporte ficticio", "cash-manual-in");
        services.cash().registerMovement(CashDirection.EXPENSE, CashCategory.SERVICES,
                new BigDecimal("5.00"), LocalDateTime.now(), "Servicio ficticio", "cash-manual-out");
        var summary = services.cash().summarize(today, today);

        assertEquals(saleTotal, summary.invoicedSales());
        assertEquals(initialPayment, summary.saleReceipts());
        assertEquals(new BigDecimal("20.00"), summary.generalDebtReceipts());
        assertEquals(new BigDecimal("25.00"), summary.otherIncome());
        assertEquals(new BigDecimal("5.00"), summary.expenses());
        assertEquals(initialPayment.add(new BigDecimal("45.00")), summary.cashIn());
        assertEquals(initialPayment.add(new BigDecimal("40.00")), summary.netCash());
    }
}
