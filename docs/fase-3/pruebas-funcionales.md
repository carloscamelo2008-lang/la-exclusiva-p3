# Plan de pruebas funcionales inicial

**Estado:** casos propuestos; aún no ejecutados. Completar el resultado y la evidencia cuando existan las funcionalidades.

| ID | Escenario | Datos de prueba | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-01 | Registrar producto | Referencia ficticia con volumen, capacidad y precios válidos | Se guarda y aparece en el catálogo. | Pendiente |
| CP-02 | Validar campos obligatorios | Producto sin nombre o sin presentación | La operación se rechaza y muestra un mensaje claro en español. | Pendiente |
| CP-03 | Calcular inventario combinado | 2 canastas de una referencia de 38 unidades y 5 unidades sueltas | El total equivalente es 81 unidades. | Pendiente |
| CP-04 | Venta de unidades | Producto con stock suficiente y cantidad de 3 unidades | El total usa el precio unitario y se descuentan 3 unidades. | Pendiente |
| CP-05 | Venta de media canasta | Referencia con capacidad de canasta conocida | El total usa el precio configurado para media canasta y se descuentan las unidades equivalentes de media canasta. | Pendiente |
| CP-06 | Venta con varias referencias | Una venta incluye dos productos diferentes | El total corresponde a la suma de las líneas y cada stock se actualiza correctamente. | Pendiente |
| CP-07 | Stock insuficiente | Cantidad de venta mayor que las unidades disponibles | La venta se rechaza sin guardar parcialmente la venta ni reducir stock. | Pendiente |
| CP-08 | Cliente sin pago completo | Venta por valor ficticio de $60.000 COP y pago inicial de $20.000 COP | Total: $60.000; pagado: $20.000; saldo: $40.000. | Pendiente |
| CP-09 | Registrar abono | Saldo de cuenta de $40.000 COP y abono de $10.000 COP | El nuevo saldo es $30.000 COP y el abono queda en el historial. | Pendiente |
| CP-10 | Abono superior al saldo | Saldo de $5.000 COP y abono de $6.000 COP | La operación se rechaza según la regla inicial, sin dejar saldo negativo. | Pendiente |
| CP-11 | Crear pedido | Pedido con referencia, cantidad y fecha esperada | Se guarda como planeado o solicitado sin aumentar el stock. | Pendiente |
| CP-12 | Recepción parcial | Pedido de 4 canastas y recepción de 2 | El stock aumenta solo por las 2 canastas recibidas; quedan 2 pendientes. | Pendiente |
| CP-13 | Registrar egreso | Compra ficticia de hielo | Se registra un movimiento de tipo egreso con fecha, importe y categoría. | Pendiente |
| CP-14 | Registrar envases compartidos | Poker 250 ml y Aguilita 250 ml | Ambos apuntan al tipo compartido de envase retornable. | Pendiente |
| CP-15 | Reiniciar la aplicación | Datos de prueba guardados con el mecanismo aprobado para la etapa | El comportamiento se ajusta al mecanismo de persistencia acordado. | Pendiente |

## Registro de ejecución

Para cada prueba ejecutada, anotar la fecha, la rama o commit, el resultado real, cualquier error encontrado y el enlace o captura de la evidencia. No marcar una prueba como aprobada sin ejecutarla.
