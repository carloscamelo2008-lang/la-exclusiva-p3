# Pruebas funcionales de la fase 3

**Última verificación:** 2026-10-27, rama `feature/sales-module`, base `b7bab3c`.

## Pruebas automatizadas

`ConsoleSalesWorkflowTest.completesConsoleWorkflowFromStockSetupThroughSalePaymentAndInsufficientStock` ejecuta el recorrido con `ConsoleMenu`, `Scanner` y `PrintStream`, usando el catálogo inicial y repositorios en memoria aislados para esta prueba. Las altas de existencias, cliente y vendedor, la venta, la consulta y el abono se realizan ingresando opciones y datos por la consola. La prueba lee los IDs generados de la salida y los usa en las siguientes fases del menú.

La consola no ofrece registro ni configuración de productos. La prueba carga `InitialCatalog`, igual que `Main`; por esta limitación, los casos CP-01 y CP-02 permanecen pendientes.

| ID | Escenario verificado | Datos y resultado observado | Estado |
|---|---|---|---|
| CP-03 | Inventario de referencia con capacidad de 38 unidades | Desde el menú se registran 2 canastas y 5 unidades sueltas; la consulta informa 81 unidades. | Aprobado, automatizado |
| CP-05 | Inventario de referencia con capacidad de 16 unidades | Desde el menú se registra 1 canasta y 2 unidades sueltas; la consulta informa 18 unidades. | Aprobado, automatizado |
| CP-10 | Media canasta de Poker 750 ml | La venta usa el precio del catálogo de $45.000 COP y descuenta 8 unidades. | Aprobado, automatizado |
| CP-11 | Canasta completa de Poker 250 ml | La venta usa el precio del catálogo de $85.000 COP y descuenta 38 unidades. | Aprobado, automatizado |
| CP-13 | Venta mixta de dos referencias y presentaciones | Canasta completa de Poker 250 ml más media canasta de Poker 750 ml: total calculado con el catálogo, $130.000 COP. La consulta muestra cliente, vendedor, pago inicial de $30.000 y saldo de $100.000. | Aprobado, automatizado |
| CP-14 | Rechazo por inventario insuficiente | Se intenta vender una canasta de Poker 750 ml cuando quedan 10 unidades; no aparece otra venta ni salida de inventario, y la deuda del cliente no cambia. | Aprobado, automatizado |
| CP-15 | Pago inicial parcial | En la venta mixta de $130.000 COP se ingresan $30.000; quedan $100.000 pendientes y asociados al cliente. | Aprobado, automatizado |
| CP-16 | Abono posterior a una venta | Se abonan $25.000 de los $100.000 pendientes; la venta y el saldo del cliente quedan en $75.000. La consulta muestra dos pagos. | Aprobado, automatizado |
| — | Recuperación ante datos y opciones inválidas | Una cantidad inválida, una presentación inválida y opciones inválidas no detienen el menú; luego se puede volver al menú principal y salir. | Aprobado, automatizado |

La prueba se ejecuta en fases de `ConsoleMenu.start()` con los mismos servicios y repositorios para poder leer y reutilizar los identificadores aleatorios mostrados por la interfaz. Ninguna alta de existencias, cliente, vendedor, venta o abono se prepara llamando directamente a sus servicios.

**Evidencia de ejecución:** pruebas nuevas y afectadas: 26 ejecutadas, 0 fallos; `mvn clean verify`: 100 ejecutadas, 0 fallos, 0 errores y `BUILD SUCCESS` (Maven 3.9.16).

## Simulaciones manuales

No se realizaron simulaciones manuales en esta verificación.

## Casos pendientes

Los siguientes casos no están cubiertos por el recorrido integrado descrito arriba. Una prueba unitaria de servicio, por sí sola, no se registra aquí como verificación funcional de consola.

| ID | Escenario | Datos propuestos | Estado |
|---|---|---|---|
| CP-01 | Registrar producto | Referencia ficticia con volumen, capacidad y tarifas válidas | Pendiente |
| CP-02 | Validar campos obligatorios del producto | Producto sin nombre o presentación | Pendiente |
| CP-04 | Inventario combinado de 30 unidades por canasta | 2 canastas y 3 unidades sueltas; total equivalente 63 unidades | Pendiente |
| CP-06 | Precio por unidad menor de 750 ml | Una referencia menor de 750 ml; precio unitario de $2.500 COP | Pendiente |
| CP-07 | Precio de Poker 750 ml | Una unidad; precio unitario de $6.000 COP | Pendiente |
| CP-08 | Media canasta de referencia de 38 unidades | Una media canasta; descuento de 19 unidades y precio de $45.000 COP | Pendiente |
| CP-09 | Media canasta de referencia de 30 unidades | Una media canasta; descuento de 15 unidades y precio de $45.000 COP | Pendiente |
| CP-12 | Canasta completa de Poker 750 ml | Una canasta; descuento de 16 unidades y precio de $90.000 COP | Pendiente |
| CP-17 | Abono superior al saldo | Saldo de $5.000 y abono de $6.000 | Pendiente |
| CP-18 | Datos obligatorios de cliente o vendedor | Falta teléfono o dirección | Pendiente |
| CP-19 | Pedido no recibido | Crear pedido con mercancía pendiente | Pendiente |
| CP-20 | Recepción parcial | Pedido de 4 canastas y recepción de 2 | Pendiente |
