# Diagrama de paquetes por capas

**Estado:** propuesta inicial de arquitectura.

~~~~mermaid
flowchart TB
    subgraph UI["ui - Interacción con el usuario"]
        ConsoleMenu["ConsoleMenu"]
        FutureJavaFX["Interfaz JavaFX futura"]
    end

    subgraph SERVICE["service - Casos de uso"]
        ProductService["ProductService"]
        InventoryService["InventoryService"]
        SaleService["SaleService"]
        AccountReceivableService["AccountReceivableService"]
        PurchaseOrderService["PurchaseOrderService"]
        CashMovementService["CashMovementService"]
    end

    subgraph MODEL["model - Dominio"]
        Product["BeerProduct"]
        People["Person / Customer / Seller"]
        Sales["Sale / SaleItem / Payment"]
        Purchases["PurchaseOrder / PurchaseOrderItem"]
        Cash["CashMovement"]
    end

    subgraph PERSISTENCE["persistence - Repositorios"]
        ProductRepository["ProductRepository"]
        SaleRepository["SaleRepository"]
        CustomerRepository["CustomerRepository"]
        PurchaseOrderRepository["PurchaseOrderRepository"]
        CashMovementRepository["CashMovementRepository"]
    end

    ConsoleMenu --> ProductService
    ConsoleMenu --> SaleService
    ConsoleMenu --> AccountReceivableService
    ConsoleMenu --> PurchaseOrderService
    ConsoleMenu --> CashMovementService
    FutureJavaFX -. "reutiliza los servicios" .-> SERVICE
    ProductService --> ProductRepository
    InventoryService --> ProductRepository
    SaleService --> SaleRepository
    SaleService --> ProductService
    SaleService --> AccountReceivableService
    AccountReceivableService --> SaleRepository
    PurchaseOrderService --> PurchaseOrderRepository
    PurchaseOrderService --> ProductService
    CashMovementService --> CashMovementRepository
    ProductRepository --> Product
    SaleRepository --> Sales
    CustomerRepository --> People
    PurchaseOrderRepository --> Purchases
    CashMovementRepository --> Cash
~~~~

## Reglas de dependencia

1. La interfaz solicita casos de uso a los servicios; no accede directamente a SQL ni a los repositorios.
2. Los servicios coordinan las operaciones y validan las reglas del negocio.
3. Las entidades del modelo no dependen de la interfaz.
4. La persistencia encapsula el mecanismo de almacenamiento. PostgreSQL se incorporará en una etapa posterior.
5. Las responsabilidades del diagrama se ajustarán si durante el análisis el equipo identifica una distribución mejor.
