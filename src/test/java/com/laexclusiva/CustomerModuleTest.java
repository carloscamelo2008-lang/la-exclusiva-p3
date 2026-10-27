package com.laexclusiva;

import com.laexclusiva.model.Customer;
import com.laexclusiva.repository.CustomerRepository;
import com.laexclusiva.repository.CustomerRepositoryInterface;
import com.laexclusiva.repository.SellerRepository;
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
        sellerService = new SellerService(new SellerRepository());
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
        assertEquals(0, new BigDecimal("150.50").compareTo(customer.getBalance()));
    }

    @Test
    void testApplyPaymentToCustomer() {
        customerService.registerCustomer("C001", "Juan Perez", "3001234567", "Calle 1");
        customerService.addDebtToCustomer("C001", new BigDecimal("200.00"));
        customerService.applyPaymentToCustomer("C001", new BigDecimal("50.00"));
        
        Customer customer = customerService.getCustomerById("C001");
        assertEquals(0, new BigDecimal("150.00").compareTo(customer.getBalance()));
    }

    @Test
    void testRegisterSellerSuccessfully() {
        // 1. Registra el vendedor pasando nombre, teléfono y dirección válidos (capturando el objeto devuelto)
        var registeredSeller = sellerService.registerSeller("Maria Gomez", "3119876543", "Calle Principal 123");
        assertNotNull(registeredSeller);
        
        // 2. Recupera el vendedor mediante el ID generado por el registro
        String generatedId = registeredSeller.getId();
        var sellerOpt = sellerService.getSellerById(generatedId);
        
        // 3. Verifica que el vendedor se haya recuperado y que sus datos coincidan
        assertTrue(sellerOpt.isPresent());
        assertEquals("Maria Gomez", sellerOpt.get().getName());
        assertEquals("3119876543", sellerOpt.get().getPhone());
        assertEquals("Calle Principal 123", sellerOpt.get().getAddress());
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
        
        Customer customer = customerService.getCustomerById("C001");
        assertEquals(0, new BigDecimal("100.00").compareTo(customer.getBalance()));
    }

    @Test
    void testValidationEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> customerService.registerCustomer(null, "Test", "3001112233", "Dir"));
        assertThrows(IllegalArgumentException.class, () -> customerService.registerCustomer("  ", "Test", "3001112233", "Dir"));
        
        customerService.registerCustomer("C002", "Ana Lopez", "3002223344", "Calle 3");
        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer("C002", new BigDecimal("-10.00")));
        assertThrows(IllegalArgumentException.class, () -> customerService.addDebtToCustomer("C002", BigDecimal.ZERO));
    }

    @Test
    void testUpdateNonExistentCustomerThrowsException() {
        Customer nonExistent = new Customer("C999", "Fantasma", "3000000000", "Ninguna", BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> {
            customerRepository.update(nonExistent);
        });
    }

    @Test
    void testServiceWorksWithRepositoryInterfaceContract() {
        CustomerRepositoryInterface altRepo = new CustomerRepositoryInterface() {
            private final Map<String, Customer> db = new HashMap<>();

            @Override
            public void save(Customer c) { 
                db.put(c.getId(), new Customer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getBalance())); 
            }

            @Override
            public Customer findById(String id) { 
                Customer c = db.get(id);
                if (c == null) return null;
                return new Customer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getBalance());
            }

            @Override
            public List<Customer> findAll() { 
                List<Customer> list = new ArrayList<>();
                for (Customer c : db.values()) {
                    list.add(new Customer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getBalance()));
                }
                return list;
            }

            @Override
            public boolean existsById(String id) { return db.containsKey(id); }

            @Override
            public void update(Customer c) {
                if (!db.containsKey(c.getId())) {
                    throw new IllegalArgumentException("No existe");
                }
                db.put(c.getId(), new Customer(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getBalance()));
            }
        };

        CustomerService altService = new CustomerService(altRepo);
        altService.registerCustomer("C999", "Cliente Alternativo", "3001234567", "Calle Falsa 123");

        altService.addDebtToCustomer("C999", new BigDecimal("300.00"));
        assertEquals(0, new BigDecimal("300.00").compareTo(altRepo.findById("C999").getBalance()));

        altService.applyPaymentToCustomer("C999", new BigDecimal("100.00"));
        assertEquals(0, new BigDecimal("200.00").compareTo(altRepo.findById("C999").getBalance()));
    }
}