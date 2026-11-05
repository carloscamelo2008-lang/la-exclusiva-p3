# Modelo relacional propuesto

## Alcance y criterio

Este análisis se deriva de las entidades, repositorios y servicios actuales de La Exclusiva P3. Las tablas de catálogo, inventario, movimientos, ventas, clientes, vendedores y cuentas de usuario reflejan el código existente. No se define aquí una implementación JDBC ni se prescriben cambios de código.

Convención: nombres de tablas y columnas en `snake_case` inglés; documentación y decisiones en español. Importes en COP se almacenan como `NUMERIC(19,2)` y fechas locales actuales como `TIMESTAMP WITHOUT TIME ZONE`, en consonancia con `LocalDateTime`. La precisión monetaria deberá confirmarse antes de desplegar.

## Entidades y relaciones actuales

- `beer_product` contiene referencias, volumen, capacidad de unidades por canasta y tipo/grupo de envase. Los tres precios actuales se modelan en `product_price`, una fila por producto y código de presentación (`UNIT`, `HALF_BASKET`, `FULL_BASKET`).
- `inventory_item` mantiene exactamente una existencia por producto. `total_units` es la fuente de verdad; canastas completas y sueltas se derivan por división y residuo con la capacidad vigente del producto.
- `stock_movement` registra producto, tipo de movimiento (`movement_type`), unidades, razón y fecha (`occurred_at`). No contiene actor ni referencia a venta en el modelo actual. En Java se mapean esos campos a `StockMovement.type()` y `StockMovement.timestamp()`.
- `customer` guarda identidad/datos de contacto y saldo agregado (`balance`). `Customer.getBalance()` se mapea a esta columna; el atributo Java existente `saldo` puede mantenerse.
- `sale` registra fecha, vendedor requerido y cliente opcional. Una venta tiene al menos una `sale_line` y cero o más `sale_payment`.
- `sale_line` conserva producto, presentación, cantidad y precio unitario aplicado. Se recomienda además guardar `units_per_basket_snapshot` para que las unidades equivalentes no cambien si luego cambia la capacidad del catálogo. El subtotal es cantidad × precio histórico y no requiere persistencia independiente.
- `sale_payment` es cada pago/abono positivo de una venta, con fecha (`occurred_at`). El pago inicial positivo también es una fila. No se guarda pago cero.
- `user_account` representa credenciales, rol, estado, fecha de creación y vínculo opcional con un vendedor.

## Claves y reglas de integridad

Claves primarias: identificadores actuales `VARCHAR` para producto, cliente, vendedor y venta; `BIGINT GENERATED ... AS IDENTITY` para inventario, movimientos, líneas, pagos y usuario. El uso de claves sustitutas en las entidades hijas evita fabricar identificadores de negocio que hoy no existen.

Claves foráneas: inventario y movimientos apuntan a producto; precios apuntan a producto; líneas y pagos apuntan a venta; líneas apuntan también a producto; venta apunta a vendedor y opcionalmente a cliente; cuenta apunta opcionalmente a vendedor.

Restricciones recomendadas: cantidades y capacidades positivas; capacidad par; volumen positivo; importes de lista y de venta positivos; `customer.balance` no negativo; códigos de presentación y enums restringidos; razón, nombres e identificadores no vacíos; unicidad de usuario sobre `lower(trim(username))`; una sola fila de inventario por producto; unicidad de `(product_id, presentation_code)`; `sale.customer_id` nullable para ventas pagadas sin cliente.

Todas las FK se proponen explícitamente con `ON DELETE RESTRICT`, incluidas las de líneas y pagos a venta. Esto bloquea el borrado físico de ventas con historial financiero y protege productos/personas referenciados. No usar cascada para compensaciones: la transacción revierte los inserts completos. La anulación requerirá política explícita futura, no borrado físico.

## Precios, presentaciones y unidades históricas

`product_price` representa el precio vigente por presentación. No se deduce el precio de media/completa canasta a partir del precio unitario, pues el código permite configurar cada uno independientemente. El precio capturado en `sale_line.unit_price` es una instantánea histórica inmutable para el cálculo posterior. Las ediciones futuras de `product_price` no reescriben ventas.

La recuperación de `SaleLine` desde PostgreSQL debe construir la línea con el `unit_price` leído de la propia fila, nunca con el precio vigente de `product_price`. Puede consultarse `beer_product` para identidad y descripción, pero los datos actuales del catálogo no reemplazan los snapshots históricos.

La presentación se guarda como código estable en `sale_line.presentation_code`; cantidad significa número de presentaciones vendidas. Para reconstruir el inventario equivalente histórico se almacena `units_per_basket_snapshot` y se calcula:

- `UNIT`: `quantity` unidades.
- `HALF_BASKET`: `quantity * units_per_basket_snapshot / 2`.
- `FULL_BASKET`: `quantity * units_per_basket_snapshot`.

La capacidad snapshot evita alterar reportes históricos ante una modificación futura de `beer_product.units_per_basket`. El `SaleLine` actual toma el precio en su constructor desde `BeerProduct.priceFor(...)` y no acepta snapshots. Para rehidratarlo se necesitará una modificación controlada: una fábrica/constructor que reciba explícitamente `unit_price` y `units_per_basket_snapshot`, sin consultar el precio vigente; el flujo normal podrá seguir capturando los valores vigentes. El subtotal se deriva de `quantity * unit_price`; el total de venta, de sus líneas; el saldo, de total menos pagos.

## Deuda general y deuda de ventas

El saldo de `customer` es el agregado que actualmente cubre deuda general más deuda asignada a ventas. La asignación por ventas hoy se mantiene en el `HashMap saleDebts` de `CustomerService`. Sustituirla por una consulta derivada: para cada venta del cliente, calcular `SUM(sale_line.quantity * sale_line.unit_price) - SUM(sale_payment.amount)` y sumar los saldos resultantes. La consulta debe agregar líneas y pagos por venta antes de combinarlos para evitar multiplicar filas entre ambas colecciones. La deuda general es `customer.balance - deuda pendiente de ventas`.

Contrato de consulta propuesto: `CustomerDebtQuery.findOutstandingSalesDebt(customerId) -> BigDecimal`. `CustomerService` depende de este puerto para validar deuda general y cobertura del balance; no depende de `SaleService`. Una implementación/adaptador de persistencia calcula la deuda desde `sale`, `sale_line` y `sale_payment` (por SQL agregado o por repositorio de ventas mientras el volumen lo permita). `SaleService` conserva su dependencia de `CustomerService`, de modo que el grafo no forma un ciclo. En `Main`, crear primero el acceso de datos/transacción y repositorios, luego el adaptador `CustomerDebtQuery` usando el repositorio/consulta de ventas, después `CustomerService(customerRepository, customerDebtQuery)` y finalmente `SaleService(..., customerService, ...)`. El puerto y su constructor/inyección son un cambio futuro de Java; se documentan ahora, no se implementan en esta revisión.

Así se mantiene la separación sin duplicar el saldo de ventas: la deuda general se administra explícitamente vía saldo agregado y las ventas a crédito quedan identificadas por `sale.customer_id`, líneas y pagos. Las ventas totalmente pagadas sin cliente permanecen permitidas. La escritura de venta a crédito y el abono deben ser transaccionales para mantener sincronizados balance, venta y pagos.

**Decisión pendiente del equipo:** confirmar si el saldo agregado debe seguir siendo valor almacenado (compatible con `Customer`) o si en una etapa posterior se reemplazará por un libro de movimientos de cuenta por cobrar. El esquema inicial no crea tabla `customer_debt` ni una columna redundante `sale_debt_balance`, porque ambas duplicarían datos derivables y exigirían conciliación.

**Decisión pendiente del equipo:** identificar cómo cargar saldos históricos existentes y si los importes están expresados siempre con dos decimales COP. No es posible reconstruir de forma fiable el mapa en memoria tras reinicios previos.

## Venta, líneas y pagos

`sale (1) -> (N) sale_line`: cada venta requiere al menos una línea (la cardinalidad mínima debe validarse en servicio/transacción, pues una FK no la expresa por sí sola). `sale (1) -> (0..N) sale_payment`. El cliente es opcional, aunque el servicio exige cliente cuando queda saldo pendiente. Un pago individual nunca supera el saldo pendiente al registrarse; la suma de pagos no debe superar el total. Esta regla requiere transacción y bloqueo/aislamiento apropiado, además de validación de servicio.

### Unidad de trabajo JDBC propuesta

La operación completa usa una sola conexión JDBC compartida y una transacción PostgreSQL: venta, líneas, pago inicial, inventario, movimientos y balance confirman juntos o se revierten juntos. Ningún DAO/repository hace `commit` individual. Los contratos existentes (`ProductRepository`, `InventoryRepository`, `StockMovementRepository`, `SaleRepository`, `CustomerRepositoryInterface`, `SellerRepositoryInterface`, `UserRepository`) conservan sus firmas; la coordinación pertenece a una capa de unidad de trabajo/transacción de aplicación, no a métodos nuevos en cada contrato.

En la futura composición JDBC, el coordinador abre la conexión, desactiva autocommit y la pone en un contexto de operación; las implementaciones DAO reciben un `ConnectionProvider`/contexto compartido y obtienen de allí esa misma conexión. No la cierran ni confirman individualmente. Al terminar, el coordinador hace un único `commit`; ante cualquier excepción hace un único `rollback` y cierra/libera la conexión. El contexto debe ser explícito y acotado a la operación (no una conexión global); así se mantiene intacta la API de repositorios. Si hay llamadas fuera de una unidad de trabajo, la política de conexión/transacción debe definirse por el coordinador, sin introducir commits ocultos en los DAO.

Para registrar venta: iniciar la unidad; bloquear cliente si aplica y filas de inventario (`SELECT ... FOR UPDATE`) en orden estable por producto; validar stock y saldo; insertar `sale`, líneas con snapshots y pago inicial si es positivo; descontar inventario e insertar movimientos; actualizar `customer.balance` si queda crédito; confirmar. Cualquier error provoca rollback de todas las tablas. Validar que exista al menos una línea y que pagos no superen el total dentro de la unidad.

Para un abono: iniciar la misma unidad de trabajo, bloquear venta y cliente, recalcular el saldo pendiente desde líneas y pagos, validar importe, insertar `sale_payment` y reducir `customer.balance` por el mismo monto; el coordinador confirma o revierte conjuntamente. El orden estable de bloqueos reduce interbloqueos. La lógica de reintentos ante errores de concurrencia deberá decidirse en implementación.

`stock_movement` permanece sin `sale_id`: `StockMovement` no expone esa relación. No inferirla desde `reason`. Una ampliación futura requerirá cambiar explícitamente el modelo Java y definir la relación para salidas, entradas y ajustes.

## ON DELETE e índices

Todas las FK usan explícitamente `ON DELETE RESTRICT`, sin cascadas. En particular, no se puede borrar físicamente una venta con líneas o pagos. Los productos, clientes y vendedores referenciados también quedan protegidos. Compensaciones se resuelven revirtiendo la transacción, no borrando cascadas.

Recomendaciones de índices y eliminación de duplicados: `product_price(product_id, presentation_code)` es PK y cubre su FK por el prefijo `product_id`; `inventory_item.product_id` es UNIQUE. Considerar índices no cubiertos para `stock_movement(product_id, occurred_at)`, `sale_line(sale_id)`, `sale_line(product_id)`, `sale_payment(sale_id)`, `sale(seller_id)`, `sale(customer_id, created_at)`, `sale(created_at)` y `user_account(seller_id)`. Un índice compuesto que inicia con una columna puede cubrir búsquedas por esa columna; no agregar una copia simple sin patrón de consulta que la justifique. Confirmar solapamientos antes de crear índices físicos.

## Tablas actuales frente a módulos futuros

Actuales o respaldadas directamente por el código: `beer_product`, `product_price`, `inventory_item`, `stock_movement`, `customer`, `seller`, `sale`, `sale_line`, `sale_payment`, `user_account`.

Futuras/no incluidas: proveedores y compras/recepciones, órdenes de compra, devoluciones/anulaciones, cartera como libro de transacciones, auditoría de cambios, catálogo normalizado de grupos de envases, métodos de pago, permisos/roles configurables, sucursales, lotes y caducidad. Se excluyen porque no hay atributos o flujos equivalentes en los archivos inspeccionados.

## Decisiones para aprobación

1. Aprobar `NUMERIC(19,2)` para importes COP y límites de longitud.
2. Aprobar `units_per_basket_snapshot` y la fábrica/constructor para rehidratar snapshots históricos.
3. Aprobar `customer.balance` como nombre SQL mapeado desde el modelo Java existente.
4. Aprobar saldo agregado y deuda de ventas derivada de ventas/pagos.
5. Definir migración histórica y política de anulación; el borrado físico se restringe.
6. Mantener `stock_movement` sin `sale_id` hasta una ampliación explícita del modelo Java.

Consulte [diagrama-er.mmd](diagrama-er.mmd) y [diccionario-datos.md](diccionario-datos.md) para el esquema lógico resumido.
