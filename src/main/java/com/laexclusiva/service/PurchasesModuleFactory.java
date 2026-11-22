package com.laexclusiva.service;

import com.laexclusiva.persistence.jdbc.GoodsReceiptRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.InventoryStockAdjusterJdbc;
import com.laexclusiva.persistence.jdbc.JdbcConnectionProvider;
import com.laexclusiva.persistence.jdbc.JdbcTransactionManager;
import com.laexclusiva.persistence.jdbc.ProductRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.PurchaseOrderRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.StockMovementRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.SupplierRepositoryJdbc;
import com.laexclusiva.persistence.jdbc.TransactionBoundary;
import com.laexclusiva.repository.InMemoryGoodsReceiptRepository;
import com.laexclusiva.repository.InMemoryInventoryRepository;
import com.laexclusiva.repository.InMemoryPurchaseOrderRepository;
import com.laexclusiva.repository.InMemoryStockMovementRepository;
import com.laexclusiva.repository.InMemorySupplierRepository;
import java.util.function.Supplier;

/** Ensambla el módulo de compras independientemente de la navegación JavaFX. */
public final class PurchasesModuleFactory {
    private PurchasesModuleFactory() {}

    /**
     * Servicios que comparten la misma composición.
     * @param suppliers gestión de proveedores
     * @param orders gestión de pedidos
     * @param receipts recepción de mercancía
     */
    public record Services(SupplierService suppliers, PurchaseOrderService orders, GoodsReceiptService receipts) {}

    /**
     * Ensambla compras en memoria sobre el catálogo, inventario e historial suministrados.
     * La frontera serializa las escrituras de esta composición. El almacenamiento en memoria
     * no proporciona rollback general ni coordina escritores externos a esta frontera.
     *
     * @param products catálogo compartido
     * @param inventory existencias compartidas
     * @param movements historial compartido
     * @return servicios de compras en memoria
     */
    public static Services inMemory(ProductService products, InMemoryInventoryRepository inventory,
                                    InMemoryStockMovementRepository movements) {
        TransactionBoundary transactions = new TransactionBoundary() {
            @Override
            public synchronized <T> T inTransaction(Supplier<T> operation) {
                return operation.get();
            }
        };
        var authorization = new AuthorizationService();
        var suppliers = new InMemorySupplierRepository();
        var orders = new InMemoryPurchaseOrderRepository();
        return new Services(new SupplierService(suppliers, authorization, transactions),
                new PurchaseOrderService(orders, suppliers, products, authorization, transactions),
                new GoodsReceiptService(orders, new InMemoryGoodsReceiptRepository(), products, authorization,
                        transactions, inventory, movements));
    }

    /**
     * Ensambla compras PostgreSQL sobre el proveedor que comparte la aplicación.
     * Los repositorios con fronteras internas se unen a la transacción del mismo proveedor.
     *
     * @param provider proveedor JDBC compartido por el módulo y el resto de la aplicación
     * @return servicios de compras con transacciones JDBC
     */
    public static Services postgres(JdbcConnectionProvider provider) {
        var transactions = new JdbcTransactionManager(provider);
        var products = new ProductService(new ProductRepositoryJdbc(provider, transactions));
        var authorization = new AuthorizationService();
        var suppliers = new SupplierRepositoryJdbc(provider);
        var orders = new PurchaseOrderRepositoryJdbc(provider);
        return new Services(new SupplierService(suppliers, authorization, transactions),
                new PurchaseOrderService(orders, suppliers, products, authorization, transactions),
                new GoodsReceiptService(orders, new GoodsReceiptRepositoryJdbc(provider), products, authorization,
                        transactions, new InventoryStockAdjusterJdbc(provider), new StockMovementRepositoryJdbc(provider)));
    }
}
