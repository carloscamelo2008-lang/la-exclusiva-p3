# Diagrama de clases inicial

**Estado:** borrador de diseño. Los métodos y relaciones deben revisarse con el equipo antes de implementarse.

~~~~mermaid
classDiagram
    class Person {
        <<abstract>>
        -String id
        -String name
        -String phone
        -String address
    }

    class Customer {
        +String getCustomerId()
    }

    class Seller {
        +String getSellerId()
    }

    Person <|-- Customer
    Person <|-- Seller

    class BeerProduct {
        -String productId
        -String name
        -String brand
        -int volumeMl
        -int unitsPerCrate
        -int fullCrates
        -int looseUnits
        -BigDecimal unitPrice
        -BigDecimal halfCratePrice
        -BigDecimal fullCratePrice
        -String containerType
        -boolean returnable
        +int calculateTotalUnits()
        +void addStock(int units)
        +void removeStock(int units)
        +BigDecimal getPrice(SalePresentation presentation)
    }

    class SalePresentation {
        <<interface>>
        +int calculateUnits(BeerProduct product, int quantity)
        +BigDecimal calculateAmount(BeerProduct product, int quantity)
    }

    class UnitPresentation
    class HalfCratePresentation
    class FullCratePresentation

    SalePresentation <|.. UnitPresentation
    SalePresentation <|.. HalfCratePresentation
    SalePresentation <|.. FullCratePresentation

    class Sale {
        -String saleId
        -LocalDateTime createdAt
        -Customer customer
        -Seller seller
        -List~SaleItem~ items
        -List~Payment~ payments
        +void addItem(SaleItem item)
        +BigDecimal calculateTotal()
        +BigDecimal calculatePaid()
        +BigDecimal calculateBalance()
        +void registerPayment(Payment payment)
    }

    class SaleItem {
        -BeerProduct product
        -SalePresentation presentation
        -int quantity
        +BigDecimal calculateSubtotal()
        +int calculateUnitsToDeduct()
    }

    class Payment {
        -String paymentId
        -BigDecimal amount
        -LocalDateTime createdAt
        -PaymentType type
    }

    Sale "1" *-- "1..*" SaleItem : contiene
    Sale "1" *-- "0..*" Payment : pagos
    SaleItem --> BeerProduct
    SaleItem --> SalePresentation
    Sale --> Customer
    Sale --> Seller

    class PurchaseOrder {
        -String orderId
        -LocalDateTime createdAt
        -LocalDate expectedDate
        -PurchaseOrderStatus status
        -List~PurchaseOrderItem~ items
        +void addItem(PurchaseOrderItem item)
        +void registerReceipt(String productId, int cratesReceived)
    }

    class PurchaseOrderItem {
        -BeerProduct product
        -int cratesOrdered
        -int cratesReceived
        +int calculatePendingCrates()
    }

    PurchaseOrder "1" *-- "1..*" PurchaseOrderItem : contiene
    PurchaseOrderItem --> BeerProduct

    class CashMovement {
        -String movementId
        -CashMovementType type
        -CashMovementCategory category
        -BigDecimal amount
        -LocalDateTime createdAt
        -String description
    }

    class SaleService
    class InventoryService
    class AccountReceivableService
    class PurchaseOrderService
    class CashMovementService

    SaleService --> Sale
    SaleService --> InventoryService
    SaleService --> AccountReceivableService
    InventoryService --> BeerProduct
    AccountReceivableService --> Payment
    PurchaseOrderService --> PurchaseOrder
    PurchaseOrderService --> InventoryService
    CashMovementService --> CashMovement
~~~~

## Notas de diseño

- Person concentra los datos comunes; Customer y Seller representan roles diferentes.
- BeerProduct conserva el conteo de canastas completas y unidades sueltas, y puede calcular el total equivalente.
- SalePresentation separa el cálculo de unidades y de importe para vender por unidad, media canasta o canasta completa. La implementación debe utilizar los precios configurados por presentación, no derivarlos del precio unitario.
- Sale posee sus líneas y pagos. El saldo es el total de venta menos los pagos registrados.
- PurchaseOrder separa el pedido de la recepción física: solo la mercancía efectivamente recibida debe aumentar el inventario.
- Los movimientos de dinero deben representar efectivo realmente recibido o pagado. Una venta fiada no equivale a efectivo recibido.
- Las clases relacionadas con envases vacíos y cavas físicas quedan para el siguiente bloque de diseño; no se deben mezclar con la presentación comercial de una venta.
- BigDecimal se propone para valores monetarios. Las firmas concretas se confirmarán antes de programar los módulos en paralelo.
