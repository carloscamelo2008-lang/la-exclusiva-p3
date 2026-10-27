package com.laexclusiva.service;

import com.laexclusiva.model.BeerProduct;
import com.laexclusiva.model.InsufficientStockException;
import com.laexclusiva.model.InventoryItem;
import com.laexclusiva.model.MovementType;
import com.laexclusiva.model.StockMovement;
import com.laexclusiva.model.StockRequest;
import com.laexclusiva.model.StockSnapshot;
import com.laexclusiva.repository.InventoryRepository;
import com.laexclusiva.repository.StockMovementRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Casos de uso del inventario. No imprime menús ni conoce el almacenamiento concreto.
 *
 * GRASP: Controller (coordina el caso de uso), Low Coupling (solo conoce interfaces y el
 * ProductService). SOLID: SRP (solo inventario) y DIP (depende de abstracciones).
 *
 * Contrato público para el futuro módulo de ventas:
 *   addStock(id, canastas, sueltas, razón) -> ingresar mercancía (también la usará la recepción
 *                                             de pedidos); registra un movimiento ENTRY
 *   discountSale(líneas, razón)            -> descontar una venta; rechaza toda la venta si
 *                                             alguna referencia no tiene stock suficiente
 *   canFulfill(líneas)                     -> consultar si una venta es posible sin modificar nada
 *   availableUnits(id) / getStock(id)      -> existencias de un producto
 *   movementsOf(id) / allMovements()       -> historial de movimientos
 * Los datos de entrada inválidos (razón vacía, cantidades que desbordan un int, líneas nulas)
 * se rechazan con IllegalArgumentException ANTES de modificar el inventario.
 */
public class InventoryService {

    private final ProductService productService;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;

    public InventoryService(ProductService productService,
                            InventoryRepository inventoryRepository,
                            StockMovementRepository movementRepository) {
        this.productService = productService;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    // ------------------------------------------------------------------ ENTRADAS

    /**
     * Ingresa mercancía como canastas completas + unidades sueltas y registra el movimiento.
     * Toda la entrada se valida antes de modificar existencias.
     *
     * @throws IllegalArgumentException si el producto no existe, la razón es nula o en blanco,
     *                                  las cantidades son inválidas o el total desborda un int
     */
    public void addStock(String productId, int fullBaskets, int looseUnits, String reason) {
        // Bloque 0: la razón se valida ANTES de tocar el stock (el movimiento la exige).
        StockMovement.requireReason(reason);

        // Bloque 1: el producto debe existir en el catálogo (lanza error si no existe).
        BeerProduct product = productService.findById(productId);

        // Bloque 2: convertir a unidades equivalentes con la regla del producto.
        int units = product.equivalentUnits(fullBaskets, looseUnits);
        if (units <= 0) {
            throw new IllegalArgumentException("Debe ingresar al menos una unidad.");
        }

        // Bloque 3: obtener las existencias del producto (o crearlas si es la primera vez).
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> new InventoryItem(product));

        // Bloque 4: aplicar el aumento, guardar y dejar rastro en el historial.
        // item.add valida el desbordamiento antes de cambiar el total.
        item.add(units);
        inventoryRepository.save(item);
        movementRepository.save(new StockMovement(productId, MovementType.ENTRY, units,
                reason, LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ SALIDAS

    /**
     * Descuenta una venta completa: primero valida todas las líneas y solo después descuenta.
     *
     * Garantía actual: si alguna referencia no tiene stock suficiente, la operación se rechaza
     * con InsufficientStockException sin descontar NINGUNA referencia de esa venta. Lo mismo
     * ocurre si la entrada es inválida (líneas nulas, razón vacía, cantidades que desbordan un int).
     * Cubre la prueba P6.
     *
     * Alcance: para el almacenamiento actual en memoria, los fallos de aplicación se compensan
     * con snapshots de existencias y eliminación de movimientos creados. No es una transacción
     * durable: si una operación de compensación también falla, el estado puede quedar parcial.
     *
     * @throws IllegalArgumentException   si la venta está vacía, hay líneas nulas, la razón es nula
     *                                    o en blanco, un producto no existe o las cantidades desbordan
     * @throws InsufficientStockException si alguna referencia no tiene existencias suficientes
     */
    public DiscountReceipt discountSale(List<StockRequest> lines, String reason) {
        // Bloque 1: validar la entrada completa antes de modificar nada.
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene productos.");
        }
        StockMovement.requireReason(reason);

        // Bloque 2: calcular cuántas unidades necesita cada producto (suma líneas repetidas).
        Map<String, Integer> required = requiredUnitsByProduct(lines);

        // Bloque 3: VALIDAR todo antes de modificar nada.
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int available = availableUnits(entry.getKey());
            if (entry.getValue() > available) {
                throw new InsufficientStockException(entry.getKey(), entry.getValue(), available);
            }
        }

        // La operación devuelve un recibo de compensación para que SaleService pueda
        // revertir también stock e historial si falla después (cliente o venta).
        DiscountReceipt receipt = new DiscountReceipt();
        try {
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                InventoryItem item = inventoryRepository.findByProductId(entry.getKey()).orElseThrow();
                receipt.previousStock.put(entry.getKey(), item.copy());
                item.remove(entry.getValue());
                inventoryRepository.save(item);
                StockMovement movement = new StockMovement(entry.getKey(), MovementType.EXIT,
                        entry.getValue(), reason, LocalDateTime.now());
                // Guardarlo en el recibo antes de llamar a save permite quitarlo si el
                // repositorio alcanza a persistir y después arroja una excepción.
                receipt.createdMovements.add(movement);
                movementRepository.save(movement);
            }
            return receipt;
        } catch (RuntimeException | Error failure) {
            try {
                rollbackDiscount(receipt);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /**
     * Revierte el stock y los movimientos de una salida ya aplicada. Está pensado para el uso
     * secuencial del almacenamiento actual en memoria; las excepciones de los repositorios de
     * compensación se propagan para que el llamador no afirme atomicidad que no pudo comprobar.
     */
    public void rollbackDiscount(DiscountReceipt receipt) {
        if (receipt == null) {
            throw new IllegalArgumentException("El recibo de descuento es obligatorio.");
        }
        if (receipt.rolledBack) {
            return;
        }

        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        for (int index = receipt.createdMovements.size() - 1; index >= 0; index--) {
            try {
                movementRepository.remove(receipt.createdMovements.get(index));
            } catch (RuntimeException failure) {
                if (runtimeFailure == null) runtimeFailure = failure;
                else runtimeFailure.addSuppressed(failure);
            } catch (Error failure) {
                if (errorFailure == null) errorFailure = failure;
                else errorFailure.addSuppressed(failure);
            }
        }

        List<Map.Entry<String, InventoryItem>> stockEntries =
                new java.util.ArrayList<>(receipt.previousStock.entrySet());
        for (int index = stockEntries.size() - 1; index >= 0; index--) {
            try {
                inventoryRepository.save(stockEntries.get(index).getValue().copy());
            } catch (RuntimeException failure) {
                if (runtimeFailure == null) runtimeFailure = failure;
                else runtimeFailure.addSuppressed(failure);
            } catch (Error failure) {
                if (errorFailure == null) errorFailure = failure;
                else errorFailure.addSuppressed(failure);
            }
        }
        if (runtimeFailure != null) {
            if (errorFailure != null) runtimeFailure.addSuppressed(errorFailure);
            throw runtimeFailure;
        }
        if (errorFailure != null) {
            throw errorFailure;
        }
        receipt.rolledBack = true;
    }

    /** Recibo privado al flujo de venta con los snapshots necesarios para compensar. */
    public static final class DiscountReceipt {
        private final Map<String, InventoryItem> previousStock = new LinkedHashMap<>();
        private final List<StockMovement> createdMovements = new java.util.ArrayList<>();
        private boolean rolledBack;

        private DiscountReceipt() { }
    }

    /**
     * Responde si la venta es posible, sin modificar el inventario.
     * Devuelve false si no hay líneas o falta stock.
     *
     * @throws IllegalArgumentException si un producto no existe, hay líneas nulas o las cantidades desbordan
     */
    public boolean canFulfill(List<StockRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : requiredUnitsByProduct(lines).entrySet()) {
            if (entry.getValue() > availableUnits(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ CONSULTAS

    /** Unidades equivalentes disponibles de un producto (0 si nunca se ha ingresado). */
    public int availableUnits(String productId) {
        productService.findById(productId); // valida que el producto exista
        return inventoryRepository.findByProductId(productId)
                .map(InventoryItem::totalUnits)
                .orElse(0);
    }

    /** Existencias de un producto expresadas como canastas completas + unidades sueltas. */
    public StockSnapshot getStock(String productId) {
        BeerProduct product = productService.findById(productId);
        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> new InventoryItem(product)); // ítem vacío, no se guarda
        return new StockSnapshot(productId, item.fullBaskets(), item.looseUnits(), item.totalUnits());
    }

    /** Historial de movimientos de un producto. */
    public List<StockMovement> movementsOf(String productId) {
        return movementRepository.findByProductId(productId);
    }

    /** Historial completo de movimientos. */
    public List<StockMovement> allMovements() {
        return movementRepository.findAll();
    }

    // ------------------------------------------------------------------ AUXILIAR

    /**
     * Traduce las líneas de venta a unidades por producto. La cantidad de unidades de cada
     * presentación la decide el propio producto (BeerProduct.unitsFor), no este servicio.
     * Usa aritmética exacta: si una línea o el acumulado de un producto desborda un int,
     * se rechaza la venta (nada se ha modificado todavía en este punto).
     */
    private Map<String, Integer> requiredUnitsByProduct(List<StockRequest> lines) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (StockRequest line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("La venta contiene una línea nula.");
            }
            BeerProduct product = productService.findById(line.productId());
            try {
                int units = Math.multiplyExact(product.unitsFor(line.presentation()), line.quantity());
                required.merge(line.productId(), units, Math::addExact);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("La cantidad solicitada de " + line.productId()
                        + " es demasiado grande: el total de unidades supera el máximo permitido.");
            }
        }
        return required;
    }
}
