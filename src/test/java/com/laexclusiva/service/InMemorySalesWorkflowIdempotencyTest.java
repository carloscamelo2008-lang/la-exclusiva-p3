package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.CashCategory;
import com.laexclusiva.model.PaymentMethod;
import com.laexclusiva.model.Permission;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePresentation;
import com.laexclusiva.model.Seller;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.ui.ApplicationServices;
import com.laexclusiva.testutil.IdentityTestFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InMemorySalesWorkflowIdempotencyTest {
    @ParameterizedTest
    @CsvSource({"EFECTIVO, TRANSFERENCIA", "TRANSFERENCIA, EFECTIVO"})
    void tracesInitialAndInstallmentMethodsWithoutDuplicatingReceipts(PaymentMethod initialMethod,
                                                                      PaymentMethod installmentMethod) {
        var services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        var seller = services.sellers().registerSeller("Vendedor", "3000000001", "Dirección");
        var product = services.products().listAll().get(0);
        BigDecimal total = product.priceFor(SalePresentation.UNIT);
        BigDecimal payment = new BigDecimal("1.00");
        String customerId = "CUSTOMER-PAYMENT";
        services.customers().registerCustomer(customerId, "Cliente", "3000000002", "Dirección", total);
        services.inventory().addStock(product.getId(), 0, 2, "Fixture unitario");
        var request = List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 1));
        var fixture = new IdentityTestFixture();
        var admin = fixture.issue(UserRole.ADMINISTRATOR).session();
        var sales = services.sales();

        Sale sale = fixture.authorization().execute(admin, Permission.REGISTER_SALE,
                () -> sales.registerSale(request, seller.getId(), customerId, payment, "sale", initialMethod));
        assertEquals(sale.getId(), fixture.authorization().execute(admin, Permission.REGISTER_SALE,
                () -> sales.registerSale(request, seller.getId(), customerId, payment, "sale", initialMethod)).getId());
        assertThrows(IllegalArgumentException.class, () -> fixture.authorization().execute(admin, Permission.REGISTER_SALE,
                () -> sales.registerSale(request, seller.getId(), customerId, payment, "sale", installmentMethod)));

        sales.registerPayment(sale.getId(), payment, "installment", installmentMethod);
        assertEquals(2, sales.registerPayment(sale.getId(), payment, "installment", installmentMethod).getPayments().size());
        assertThrows(IllegalArgumentException.class,
                () -> sales.registerPayment(sale.getId(), payment, "installment", initialMethod));
        assertEquals(1, sales.listAll().size());
        assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
        assertEquals(0, total.subtract(payment.add(payment))
                .compareTo(services.customers().getCustomerById(customerId).getBalance()));
        var initial = services.cash().listMovements(null, null, CashCategory.SALE_INITIAL_PAYMENT);
        var installments = services.cash().listMovements(null, null, CashCategory.SALE_INSTALLMENT);
        assertEquals(1, initial.size());
        assertEquals(1, installments.size());
        assertEquals(initialMethod, initial.get(0).paymentMethod());
        assertEquals(installmentMethod, installments.get(0).paymentMethod());
    }

    @Test
    void tracesGeneralPaymentAndProtectsBalanceAndCashOnRetries() {
        var services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        String customerId = "CUSTOMER-GENERAL";
        services.customers().registerCustomer(customerId, "Cliente", "3000000003", "Dirección");
        var debt = services.generalDebt();
        debt.registerDebt(customerId, new BigDecimal("30.00"), "General", "Cargo", "charge");
        var amount = new BigDecimal("20.00");
        var first = debt.registerPayment(customerId, amount, "General", "Abono", "receipt", PaymentMethod.TRANSFERENCIA);
        assertEquals(first, debt.registerPayment(customerId, amount, "General", "Abono", "receipt",
                PaymentMethod.TRANSFERENCIA));
        assertThrows(IllegalArgumentException.class,
                () -> debt.registerPayment(customerId, amount, "General", "Abono", "receipt", PaymentMethod.EFECTIVO));
        assertEquals(new BigDecimal("10.00"), debt.findBalance(customerId).generalDebt());
        assertEquals(2, debt.listMovements(customerId, null, null, null).size());
        var receipts = services.cash().listMovements(null, null, CashCategory.GENERAL_DEBT_PAYMENT);
        assertEquals(1, receipts.size());
        assertEquals(amount, receipts.get(0).amount());
        assertEquals(PaymentMethod.TRANSFERENCIA, receipts.get(0).paymentMethod());
    }

    @Test
    void saleRetryWithSameKeyDoesNotRepeatInMemoryEffects() {
        ApplicationServices services = ApplicationServices.create(ApplicationServices.MEMORY_MODE, Map.of());
        Seller seller = services.sellers().registerSeller("Vendedor ficticio", "3000000001", "Dirección ficticia");
        BeerProduct product = services.products().listAll().get(0);
        services.inventory().addStock(product.getId(), 0, 2, "Fixture unitario");
        List<StockRequest> request = List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 1));
        String key = "memory-sale-key";

        var identityFixture = new com.laexclusiva.testutil.IdentityTestFixture();

        var testAuthorization = identityFixture.authorization();

        var testAdmin = identityFixture.issue(com.laexclusiva.model.UserRole.ADMINISTRATOR).session();

        var worker = identityFixture.issue(com.laexclusiva.model.UserRole.WORKER).session();
        assertThrows(SecurityException.class, () -> testAuthorization.execute(worker,
                com.laexclusiva.model.Permission.REGISTER_SALE,
                () -> services.sales().registerSale(request, seller.getId(), null,
                        product.priceFor(SalePresentation.UNIT), key)));
        Sale first = testAuthorization.execute(testAdmin, com.laexclusiva.model.Permission.REGISTER_SALE, () -> services.sales().registerSale(request, seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key));
        Sale replay = testAuthorization.execute(testAdmin, com.laexclusiva.model.Permission.REGISTER_SALE, () -> services.sales().registerSale(request, seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key));

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, services.sales().listAll().size());
        assertEquals(1, services.inventory().getStock(product.getId()).totalUnits());
        assertThrows(IllegalArgumentException.class, () -> testAuthorization.execute(testAdmin, com.laexclusiva.model.Permission.REGISTER_SALE, () -> services.sales().registerSale(
                List.of(new StockRequest(product.getId(), SalePresentation.UNIT, 2)), seller.getId(), null,
                product.priceFor(SalePresentation.UNIT), key)));
        assertEquals(1, services.sales().listAll().size());
    }
}
