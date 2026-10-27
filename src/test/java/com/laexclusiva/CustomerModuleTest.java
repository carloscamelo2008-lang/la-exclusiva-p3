package com.laexclusiva;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.SellerRepository;
import com.laexclusiva.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerModuleTest {

    private CustomerRepository customerRepository;
    private SellerRepository sellerRepository;
    private CustomerService customerService;

    @BeforeEach
    public void setUp() {
        customerRepository = new CustomerRepository();
        sellerRepository = new SellerRepository();
        customerService = new CustomerService(customerRepository);
    }

    @Test
    public void testInitialDebtIsZero() {
        Customer customer = customerService.registerCustomer("Carlos Perez", "3001234567", "Calle 10 #20-30");
        assertEquals(0, BigDecimal.ZERO.compareTo(customer.getCurrentDebt()));
    }

    @Test
    public void testAccumulateTwoDebts() {
        Customer customer = customerService.registerCustomer("Maria Gomez", "3019876543", "Carrera 5 #12-14");
        customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(25000L));
        customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(15000L));

        Customer updated = customerService.getCustomerById(customer.getId()).get();
        assertEquals(0, BigDecimal.valueOf(40000L).compareTo(updated.getCurrentDebt()));
    }

    @Test
    public void testPartialPayment() {
        Customer customer = customerService.registerCustomer("Juan Lopez", "3021112233", "Av. Central 45");
        customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(50000L));
        customerService.applyPaymentToCustomer(customer.getId(), BigDecimal.valueOf(20000L));

        Customer updated = customerService.getCustomerById(customer.getId()).get();
        assertEquals(0, BigDecimal.valueOf(30000L).compareTo(updated.getCurrentDebt()));
    }

    @Test
    public void testExactPayment() {
        Customer customer = customerService.registerCustomer("Ana Silva", "3034445566", "Diagonal 8 #4-2");
        customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(30000L));
        customerService.applyPaymentToCustomer(customer.getId(), BigDecimal.valueOf(30000L));

        Customer updated = customerService.getCustomerById(customer.getId()).get();
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getCurrentDebt()));
    }

    @Test
    public void testRejectPaymentGreaterThanDebt() {
        Customer customer = customerService.registerCustomer("Luis Torres", "3047778899", "Calle 2 #3-1");
        customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(20000L));

        assertThrows(IllegalArgumentException.class, () -> {
            customerService.applyPaymentToCustomer(customer.getId(), BigDecimal.valueOf(25000L));
        });

        Customer updated = customerService.getCustomerById(customer.getId()).get();
        assertEquals(0, BigDecimal.valueOf(20000L).compareTo(updated.getCurrentDebt()));
    }

    @Test
    public void testRejectNullOrNegativeAmounts() {
        Customer customer = customerService.registerCustomer("Pedro Picapiedra", "3050001122", "Roca Viva");

        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer(customer.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer(customer.getId(), BigDecimal.valueOf(-1000L)));
        assertThrows(IllegalArgumentException.class, () -> customerService.applyPaymentToCustomer(customer.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> customerService.applyPaymentToCustomer(customer.getId(), BigDecimal.valueOf(-1000L)));
    }

    @Test
    public void testRepositoryRejectDuplicateId() {
        Customer customer1 = new Customer("CLI-001", "Pedro", "3000000000", "Calle 1", BigDecimal.ZERO);
        Customer customer2 = new Customer("CLI-001", "Juan", "3011111111", "Calle 2", BigDecimal.ZERO);

        customerRepository.save(customer1);
        assertThrows(IllegalArgumentException.class, () -> customerRepository.save(customer2));
    }

    @Test
    public void testRejectInvalidPersonData() {
        assertThrows(IllegalArgumentException.class, () -> new Customer("", "3000000000", "Calle 1"));
        assertThrows(IllegalArgumentException.class, () -> new Seller("Carlos", null, "Calle 1"));
    }
}