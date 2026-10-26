# Plan de pruebas funcionales inicial

**Estado:** casos propuestos; aún no ejecutados. Completar el resultado y la evidencia después de probar cada funcionalidad.

| ID | Escenario | Datos de prueba | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-01 | Registrar producto | Referencia ficticia con volumen, capacidad y tarifas válidas | El producto se guarda y aparece en el catálogo. | Pendiente |
| CP-02 | Validar campos obligatorios | Producto sin nombre o presentación | Se rechaza la operación con mensaje en español. | Pendiente |
| CP-03 | Inventario combinado de 38 unidades | 2 canastas y 5 unidades sueltas | Total equivalente: 81 unidades. | Pendiente |
| CP-04 | Inventario combinado de 30 unidades | 2 canastas y 3 unidades sueltas | Total equivalente: 63 unidades. | Pendiente |
| CP-05 | Inventario combinado de 16 unidades | 1 canasta y 2 unidades sueltas | Total equivalente: 18 unidades. | Pendiente |
| CP-06 | Precio por unidad menor de 750 ml | Una referencia menor de 750 ml | Precio unitario aplicado: $2.500 COP. | Pendiente |
| CP-07 | Precio de Poker 750 ml | Una unidad | Precio unitario aplicado: $6.000 COP. | Pendiente |
| CP-08 | Media canasta de referencia de 38 unidades | Una media canasta | Se descuentan 19 unidades; precio $45.000 COP. | Pendiente |
| CP-09 | Media canasta de referencia de 30 unidades | Una media canasta | Se descuentan 15 unidades; precio $45.000 COP. | Pendiente |
| CP-10 | Media canasta de Poker 750 ml | Una media canasta | Se descuentan 8 unidades; precio $45.000 COP. | Pendiente |
| CP-11 | Canasta completa menor de 750 ml | Una canasta de referencia con capacidad 38 o 30 | Se descuentan 38 o 30 unidades; precio $85.000 COP. | Pendiente |
| CP-12 | Canasta completa de Poker 750 ml | Una canasta | Se descuentan 16 unidades; precio $90.000 COP. | Pendiente |
| CP-13 | Venta mixta | Dos referencias en una operación | El total suma las líneas y cada inventario se actualiza correctamente. | Pendiente |
| CP-14 | Stock insuficiente | Cantidad superior a las existencias | La venta se rechaza sin cambios parciales en inventario. | Pendiente |
| CP-15 | Pago inicial parcial | Venta por $60.000 y pago inicial de $20.000 | Total $60.000; pagado $20.000; saldo $40.000. | Pendiente |
| CP-16 | Registrar abono | Saldo de $40.000 y abono de $10.000 | Nuevo saldo $30.000 y abono visible en el historial. | Pendiente |
| CP-17 | Abono superior al saldo | Saldo de $5.000 y abono de $6.000 | Se rechaza y el saldo no se vuelve negativo. | Pendiente |
| CP-18 | Datos de cliente o vendedor | Falta teléfono o dirección | El registro se rechaza hasta completar datos obligatorios. | Pendiente |
| CP-19 | Pedido no recibido | Crear pedido con mercancía pendiente | El pedido se registra sin cambiar existencias. | Pendiente |
| CP-20 | Recepción parcial | Pedido de 4 canastas y recepción de 2 | Solo se agregan al inventario las 2 canastas recibidas. | Pendiente |

## Registro de ejecución

Por cada caso ejecutado anotar fecha, rama/commit, resultado real, defectos encontrados y evidencia. No marcar una prueba como aprobada sin ejecutarla.
