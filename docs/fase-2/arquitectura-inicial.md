# Arquitectura inicial por capas

**Estado:** propuesta de diseño, pendiente de revisión del equipo.

## Objetivo

Separar interacción, reglas de negocio y almacenamiento para que el programa de consola funcione en el primer avance y que JavaFX y PostgreSQL puedan incorporarse después sin duplicar los casos de uso.

## Capas

### Model

Representa conceptos y reglas propias de las entidades del negocio, como productos, clientes, vendedores, ventas, líneas de venta, pagos y pedidos. No presenta menús ni accede directamente al almacenamiento.

### Persistence

Define contratos de repositorio e implementaciones para guardar y recuperar datos. En el primer avance se propone una implementación temporal en memoria mediante colecciones. Los datos se reinician al cerrar el programa. PostgreSQL/JDBC se añadirá posteriormente detrás de los mismos contratos.

### Service

Coordina casos de uso y valida reglas que involucran varias entidades.

- ProductService: catálogo y datos del producto.
- InventoryService: canastas completas, unidades sueltas y equivalencias.
- SaleService: líneas de venta, cálculo del total y validación previa del inventario.
- AccountReceivableService: pagos, abonos y saldos pendientes.
- PurchaseOrderService: creación y recepción de pedidos.
- CashMovementService: categorías y consulta de ingresos y egresos.

### UI

ConsoleMenu presenta las opciones, recibe entradas y muestra mensajes en español. No debe contener los cálculos principales ni acceder directamente a los repositorios. La interfaz JavaFX final reutilizará los mismos servicios.

## Diagrama de dependencias

~~~~mermaid
flowchart TD
    UI["UI: consola ahora / JavaFX después"] --> SERVICE["Service: casos de uso"]
    SERVICE --> MODEL["Model: entidades y reglas"]
    SERVICE --> PERSISTENCE["Persistence: repositorios"]
    PERSISTENCE --> MODEL
~~~~

## Paquetes propuestos

~~~~text
src/main/java/com/laexclusiva/
├── Main.java
├── model/
├── persistence/
├── service/
└── ui/
    └── ConsoleMenu.java
~~~~

No se crearán clases vacías solo para completar la estructura. Cada clase debe tener una responsabilidad definida y relacionarse con un requerimiento verificable.

## Decisiones técnicas

- Java 17 y Maven.
- Identificadores del código en inglés; documentación y mensajes de usuario en español.
- BigDecimal para importes monetarios.
- Tarifas configuradas explícitamente por producto y presentación.
- Media canasta descuenta la mitad de la capacidad configurada.
- Antes de una venta se validan todas las líneas y existencias para evitar cambios parciales.
- La persistencia en memoria es temporal y debe sustituirse al integrar PostgreSQL.

## Decisiones pendientes

- Contratos públicos de servicios y repositorios.
- Datos de prueba iniciales y sus cantidades.
- Estado de pago y representación de abonos.
- Reglas concretas de recepción parcial de pedidos.
