# Arquitectura inicial por capas

**Estado:** propuesta de diseño; validar con el equipo antes de cerrar las clases definitivas.

## 1. Objetivo

Separar la interacción con el usuario, las reglas de negocio y el acceso a los datos para permitir que el programa de consola funcione ahora y que posteriormente se pueda incorporar JavaFX y PostgreSQL sin trasladar las reglas a la interfaz.

## 2. Capas

### Model

Representa entidades y conceptos del negocio: productos, personas, ventas, líneas de venta, pagos, pedidos y movimientos de dinero. Las clases de dominio contienen cálculos y reglas propias de sus datos, sin mostrar menús ni ejecutar SQL.

### Persistence

Encapsula la lectura y escritura de datos. Las operaciones de servicio no deben depender directamente de sentencias SQL ni de archivos concretos.

Para el avance inmediato, el equipo puede posponer la integración definitiva con PostgreSQL y mantener separada la capa de persistencia. Si se acuerda una persistencia temporal local, deberá quedar aislada detrás de repositorios para facilitar su sustitución posterior. La elección del mecanismo temporal debe confirmarse al revisar esta rama.

### Service

Coordina casos de uso y reglas que involucran varias entidades, por ejemplo, registrar una venta, verificar existencias, calcular el saldo pendiente o recibir mercancía.

Servicios sugeridos:

- ProductService: catálogo e inventario.
- SaleService: venta y total de la operación.
- AccountReceivableService: pagos, abonos y saldo pendiente.
- PurchaseOrderService: pedidos y recepción de mercancía.
- CashMovementService: clasificación y consulta de entradas y salidas de dinero.

### UI

Para el avance inicial, ConsoleMenu presenta opciones en español, captura entradas y muestra resultados. No debe consultar repositorios directamente ni contener los cálculos principales. La interfaz JavaFX de la entrega final reutilizará los servicios en lugar de duplicar reglas de negocio.

## 3. Dependencias

~~~~mermaid
flowchart TD
    UI["UI: consola ahora / JavaFX después"] --> SERVICE["Service: casos de uso"]
    SERVICE --> MODEL["Model: entidades y reglas propias"]
    SERVICE --> PERSISTENCE["Persistence: repositorios"]
    PERSISTENCE --> MODEL
~~~~

La UI invoca servicios. Los servicios coordinan reglas de negocio y usan repositorios. La persistencia convierte datos almacenados en objetos de dominio y viceversa.

## 4. Paquetes propuestos

~~~~text
src/main/java/com/laexclusiva/
├── Main.java
├── model/
├── persistence/
├── service/
└── ui/
    └── ConsoleMenu.java

docs/
├── ai-usage/
├── fase-1/
├── fase-2/
└── fase-3/

database/
└── README.md
~~~~

No se deben crear clases vacías solo para completar el árbol. Cada clase debe tener una responsabilidad definida en el diagrama inicial y un criterio de aceptación en la tabla de tareas.

## 5. Decisiones técnicas iniciales

- Compilación con Java 17 mediante Maven.
- IDE compatibles: IntelliJ IDEA y NetBeans.
- Interacción por consola en el primer avance.
- JavaFX previsto para la interfaz de la entrega final.
- PostgreSQL/JDBC previsto para una etapa posterior.
- Los valores de dinero deben manejarse con un tipo decimal apropiado para evitar errores de redondeo.
- Los precios por unidad, media canasta y canasta completa se configuran explícitamente por referencia y presentación.

## 6. Integridad de las operaciones

La venta debe validar todos los artículos y sus existencias antes de aplicar descuentos de inventario. Si una validación falla, no debe quedar una venta registrada a medias ni perderse stock.

Cuando se integre PostgreSQL, las operaciones relacionadas se ejecutarán mediante una transacción para que el registro de la venta, sus líneas, los pagos y los movimientos asociados se guarden de manera consistente.

## 7. Decisiones pendientes

- Mecanismo temporal de persistencia para el primer avance, mientras se pospone PostgreSQL.
- Firmas concretas de los servicios y repositorios.
- Forma de representar el estado de pago de cada venta.
- Modelo definitivo de pedido y recepción parcial.
- Límites del módulo de envases retornables y de cavas físicas.
