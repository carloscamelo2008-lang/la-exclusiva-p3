package com.laexclusiva;

import com.laexclusiva.model.Customer;
import com.laexclusiva.model.Seller;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.service.CustomerService;
import com.laexclusiva.service.SellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerModuleTest {

    private CustomerRepositoryInterface customerRepository;
    private CustomerService customerService;
    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        customerRepository = new CustomerRepository();
        customerService = new CustomerService(customerRepository);
        sellerService = new SellerService();
    }

    @Test
    void testRegisterCustomerSuccessfully() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        Customer customer = customerService.getCustomerById("C001");
        assertNotNull(customer);
        assertEquals("Juan Perez", customer.getName());
    }

    @Test
    void testAddDebtToCustomer() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        customerService.addDebtToCustomer("C001", new BigDecimal("150.50"));
        
        Customer customer = customerService.getCustomerById("C001");
        assertEquals(0, new BigDecimal("150.50").compareTo(customer.getCurrentBalance()));
    }

    @Test
    void testApplyPaymentToCustomer() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        customerService.addDebtToCustomer("C001", new BigDecimal("200.00"));
        customerService.applyPaymentToCustomer("C001", new BigDecimal("50.00"));
        
        Customer customer = customerService.getCustomerById("C001");
        assertEquals(0, new BigDecimal("150.00").compareTo(customer.getCurrentBalance()));
    }

    @Test
    void testRegisterSellerSuccessfully() {
        sellerService.registerSeller("S001", "Maria Gomez", "3119876543", "Avenida 2");
        Seller seller = sellerService.getSellerById("S001");
        assertNotNull(seller);
        assertEquals("Maria Gomez", seller.getName());
    }

    @Test
    void testDuplicateCustomerThrowsException() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.registerCustomer("C001", "Duplicado", "3007654321", "Calle 2");
        });
    }

    @Test
    void testNegativeBalanceThrowsException() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        customerService.addDebtToCustomer("C001", new BigDecimal("100.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            customerService.applyPaymentToCustomer("C001", new BigDecimal("150.00"));
        });
    }

    @Test
    void testValidationEdgeCases() {
        // ID nulo o vacío
        assertThrows(IllegalArgumentException.class, () -> customerService.registerCustomer(null, "Test", "3001112233", "Dir"));
        assertThrows(IllegalArgumentException.class, () -> customerService.registerCustomer("  ", "Test", "3001112233", "Dir"));
        
        // Montos negativos o cero
        customerService.registerCustomer("C002", "Ana Lopez", "3002223344", "Calle 3");
        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer("C002", new BigDecimal("-10.00")));
        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer("C002", BigDecimal.ZERO));
    }

    @Test
    void testUpdateNonExistentCustomerThrowsException() {
        Customer nonExistent = new Customer("C999", "Fantasma", "3000000000", "Ninguna");
        assertThrows(IllegalArgumentException.class, () -> {
            customerRepository.update(nonExistent);
        });
    }

    @Test
    void testServiceWorksWithRepositoryInterfaceContract() {
        // Implementación alternativa (stub) para verificar el cumplimiento del contrato DIP exigido por Carlos
        CustomerRepositoryInterface altRepo = new CustomerRepositoryInterface() {
            private final Map<String, Customer> db = new HashMap<>();

            @Override
            public void save(Customer c) { db.put(c.getId(), c); }

            @Override
            public Customer findById(String id) { return db.get(id); }

            @Override
            public List<Customer> findAll() { return new ArrayList<>(db.values()); }

            @Override
            public boolean existsById(String id) { return db.containsKey(id); }

            @Override
            public void update(Customer c) {
                if (!db.containsKey(c.getId())) {
                    throw new IllegalArgumentException("No existe");
                }
                db.put(c.getId(), c);
            }
        };

        CustomerService altService = new CustomerService(altRepo);
        altService.registerCustomer("C999", "Cliente Alternativo", "3001234567", "Calle Falsa 123");

        altService.addDebtToCustomer("C999", new BigDecimal("250.00"));

        // Verificar que el saldo actualizado queda persistido a través de la interfaz
        BigDecimal balance = altRepo.findById("C999").getCurrentBalance();
        assertEquals(0, new BigDecimal("250.00").compareTo(balance));
    }
}