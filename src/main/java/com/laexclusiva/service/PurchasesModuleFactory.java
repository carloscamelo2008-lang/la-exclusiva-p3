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
import com.laexclusiva.repository.UserRepository;
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
        throw new SecurityException("Compras requiere el autorizador de la identidad activa.");
    }

    /**
     * Ensambla compras en memoria con la autoridad de sesiones compartida por la aplicación.
     *
     * @param products catálogo compartido
     * @param inventory existencias compartidas
     * @param movements historial compartido
     * @param authorization autorizador ligado al AuthenticationService activo
     * @return servicios de compras que revalidan permisos en cada operación
     */
    public static Services inMemory(ProductService products, InMemoryInventoryRepository inventory,
                                    InMemoryStockMovementRepository movements,
                                    AuthorizationService authorization) {
        return inMemory(products, inventory, movements, authorization, null);
    }

    /**
     * Ensambla compras en memoria con los repositorios compartidos de identidad y catálogo.
     * @param products catálogo compartido con ventas e inventario
     * @param inventory repositorio de existencias compartido con inventario
     * @param movements historial de movimientos compartido con inventario
     * @param authorization autoridad ligada a las sesiones activas
     * @param auditUsers repositorio de usuarios que resuelve la autoría vigente
     * @return servicios de proveedores, pedidos y recepción
     */
    public static Services inMemory(ProductService products, InMemoryInventoryRepository inventory,
                                    InMemoryStockMovementRepository movements,
                                    AuthorizationService authorization, UserRepository auditUsers) {
        requireSessionAuthority(authorization);
        TransactionBoundary transactions = new TransactionBoundary() {
            @Override
            public synchronized <T> T inTransaction(Supplier<T> operation) {
                return operation.get();
            }
        };
        var suppliers = new InMemorySupplierRepository();
        var orders = new InMemoryPurchaseOrderRepository();
        return new Services(new SupplierService(suppliers, authorization, transactions),
                new PurchaseOrderService(orders, suppliers, products, authorization, transactions, auditUsers),
                new GoodsReceiptService(orders, new InMemoryGoodsReceiptRepository(), products, authorization,
                        transactions, inventory, movements, auditUsers));
    }

    /**
     * Ensambla compras PostgreSQL sobre el proveedor que comparte la aplicación.
     * Los repositorios con fronteras internas se unen a la transacción del mismo proveedor.
     *
     * @param provider proveedor JDBC compartido por el módulo y el resto de la aplicación
     * @return servicios de compras con transacciones JDBC
     */
    public static Services postgres(JdbcConnectionProvider provider) {
        throw new SecurityException("Compras requiere el autorizador de la identidad activa.");
    }

    /**
     * Ensambla compras PostgreSQL con el autorizador compartido por la composición de aplicación.
     *
     * @param provider proveedor JDBC compartido por el módulo y el resto de la aplicación
     * @param authorization autorizador ligado al AuthenticationService activo
     * @return servicios de compras con transacciones JDBC y permisos vigentes
     */
    public static Services postgres(JdbcConnectionProvider provider, AuthorizationService authorization) {
        return postgres(provider, authorization, new com.laexclusiva.repository.jdbc.UserRepositoryJdbc(provider));
    }

    /**
     * Ensambla compras JDBC usando la identidad persistente seleccionada por la aplicación.
     * @param provider proveedor JDBC compartido por la aplicación
     * @param authorization autoridad ligada a las sesiones activas
     * @param auditUsers repositorio de usuarios persistido con el proveedor compartido
     * @return servicios de proveedores, pedidos y recepción
     */
    public static Services postgres(JdbcConnectionProvider provider, AuthorizationService authorization,
                                    UserRepository auditUsers) {
        requireSessionAuthority(authorization);
        var transactions = new JdbcTransactionManager(provider);
        var products = new ProductService(new ProductRepositoryJdbc(provider, transactions));
        var suppliers = new SupplierRepositoryJdbc(provider);
        var orders = new PurchaseOrderRepositoryJdbc(provider);
        return new Services(new SupplierService(suppliers, authorization, transactions),
                new PurchaseOrderService(orders, suppliers, products, authorization, transactions, auditUsers),
                new GoodsReceiptService(orders, new GoodsReceiptRepositoryJdbc(provider), products, authorization,
                        transactions, new InventoryStockAdjusterJdbc(provider), new StockMovementRepositoryJdbc(provider),
                        auditUsers));
    }

    /**
     * Ensambla compras JDBC con catálogo, transacción, inventario y movimientos compartidos por la aplicación.
     * Todas las escrituras de recepción se unen a la unidad de trabajo del proveedor y se confirman
     * con el ajuste de stock, el movimiento, la recepción y el progreso de las líneas del pedido.
     * @param provider proveedor de conexiones que propaga el contexto transaccional
     * @param authorization autoridad ligada a las sesiones activas
     * @param auditUsers repositorio de identidad persistida
     * @param products catálogo JDBC compartido con ventas e inventario
     * @param transactions frontera transaccional compartida
     * @param stock ajustador JDBC de existencias basado en el mismo proveedor
     * @param movements repositorio JDBC de movimientos basado en el mismo proveedor
     * @return servicios de proveedores, pedidos y recepción
     */
    public static Services postgres(JdbcConnectionProvider provider, AuthorizationService authorization,
                                    UserRepository auditUsers, ProductService products,
                                    TransactionBoundary transactions, InventoryStockAdjusterJdbc stock,
                                    StockMovementRepositoryJdbc movements) {
        requireSessionAuthority(authorization);
        if (provider == null || auditUsers == null || products == null || transactions == null
                || stock == null || movements == null) {
            throw new IllegalArgumentException("La composición compartida de compras está incompleta.");
        }
        var suppliers = new SupplierRepositoryJdbc(provider);
        var orders = new PurchaseOrderRepositoryJdbc(provider);
        return new Services(new SupplierService(suppliers, authorization, transactions),
                new PurchaseOrderService(orders, suppliers, products, authorization, transactions, auditUsers),
                new GoodsReceiptService(orders, new GoodsReceiptRepositoryJdbc(provider), products, authorization,
                        transactions, stock, movements, auditUsers));
    }

    private static void requireSessionAuthority(AuthorizationService authorization) {
        if (authorization == null) throw new IllegalArgumentException("La autorización es obligatoria.");
        authorization.requireSessionAuthority();
    }
}
