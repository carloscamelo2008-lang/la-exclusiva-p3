# Diagrama de clases inicial

**Estado:** borrador de diseño. Debe revisarse antes de implementar y actualizarse contra el código final.

~~~~mermaid
classDiagram
    class Person {
        <<abstract>>
        -String id
        -String name
        -String phone
        -String address
    }
    class Customer
    class Seller
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

## Decisiones representadas

- Person concentra nombre, teléfono y dirección; Customer y Seller representan roles.
- BeerProduct almacena capacidad por canasta, canastas completas y unidades sueltas.
- Las presentaciones de venta calculan unidades e importes mediante un contrato común.
- Media canasta equivale a 19, 15 u 8 unidades para capacidades de 38, 30 o 16.
- Las tarifas comerciales se configuran explícitamente: menor de 750 ml, $2.500 por unidad, $45.000 media canasta y $85.000 canasta completa; Poker 750 ml, $6.000 por unidad, $45.000 media y $90.000 completa.
- Media canasta es una presentación comercial; la cava física prestada es un objeto diferente.
- Sale mantiene sus líneas y pagos. El saldo es total menos pagos.
- PurchaseOrder se separa de la recepción: solo lo recibido aumenta el inventario.
- Las clases de control detallado de envases y cavas físicas se diseñarán después.
- BigDecimal se propone para dinero. Las firmas concretas deberán confirmarse antes del desarrollo paralelo.
