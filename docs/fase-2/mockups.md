# Mockups preliminares de la interfaz final

**Propósito:** avanzar en el diseño de interacción solicitado en la fase 2, aunque el primer avance se implemente por consola.

Estos wireframes son esquemas de baja fidelidad, no pantallas funcionales. La futura interfaz se implementará con JavaFX después de estabilizar los casos de uso principales.

## 1. Panel de operación diaria

~~~~text
+-------------------------------------------------------------------+
| LA EXCLUSIVA                            Usuario: [Vendedor]        |
+-------------------------------------------------------------------+
| Ventas del día | Dinero recibido | Deudas pendientes | Stock bajo  |
|    $ 0         |      $ 0        |       $ 0        |      0      |
+-------------------------------------------------------------------+
| Accesos principales                                               |
| [Nueva venta] [Productos e inventario] [Clientes y abonos]        |
| [Pedidos de mercancía] [Movimientos de caja] [Reportes]           |
+-------------------------------------------------------------------+
| Actividad reciente                                                |
| Hora     Operación             Valor          Estado               |
| --:--    Sin operaciones       --             --                   |
+-------------------------------------------------------------------+
~~~~

**Objetivo:** que el encargado identifique rápidamente las operaciones del día sin confundir ventas con dinero efectivamente recibido.

## 2. Registrar una venta

~~~~text
+-------------------------------------------------------------------+
| NUEVA VENTA                                                       |
+-------------------------------------------------------------------+
| Cliente: [Buscar cliente... v]    Vendedor: [Seleccionar v]        |
|                                                                   |
| Producto: [Poker 250 ml v]  Presentación: [Unidad v]              |
| Cantidad: [ 1 ]             [Agregar producto]                    |
+-------------------------------------------------------------------+
| Detalle de la venta                                               |
| Producto       Presentación      Cantidad    Precio       Importe  |
| Poker 250 ml   Unidad               1       $2.500       $2.500   |
+-------------------------------------------------------------------+
| Total venta:           $ 2.500                                    |
| Pago recibido ahora:   [$ 2.500]                                  |
| Saldo pendiente:       $ 0                                        |
|                                                                   |
| [Registrar venta] [Cancelar]                                      |
+-------------------------------------------------------------------+
~~~~

**Validaciones previstas:** no permitir cantidades inválidas ni stock insuficiente; recalcular el total al agregar líneas; permitir pago parcial cuando el cliente no cubra el importe completo; mostrar el saldo antes de confirmar.

## 3. Productos e inventario

~~~~text
+-------------------------------------------------------------------+
| PRODUCTOS E INVENTARIO                                            |
+-------------------------------------------------------------------+
| Buscar: [________________]  [Nuevo producto] [Ajuste de stock]     |
+-------------------------------------------------------------------+
| Referencia      ml     Canastas     Sueltas    Total unidades      |
| Costeñita       175       2            5              81           |
| Poker           250       1            4              42           |
| Poker           750       1            2              18           |
+-------------------------------------------------------------------+
| Seleccione una referencia para ver precios por presentación.      |
| [Guardar] [Volver]                                                |
+-------------------------------------------------------------------+
~~~~

Los valores del ejemplo son ficticios. La capacidad y el total equivalente se mostrarán según la presentación real: 38, 30 o 16 unidades por canasta.

## 4. Clientes, deudas y abonos

~~~~text
+-------------------------------------------------------------------+
| CLIENTES Y CUENTAS POR COBRAR                                     |
+-------------------------------------------------------------------+
| [Nuevo cliente]   Buscar por nombre, ID o teléfono: [___________]  |
+-------------------------------------------------------------------+
| ID cliente  Nombre           Teléfono       Saldo pendiente        |
| C-0001      Cliente de prueba 3000000000      $ 25.000             |
+-------------------------------------------------------------------+
| Cuenta seleccionada                                               |
| Venta      Fecha        Total        Abonado       Saldo           |
| V-0001     --/--/----   $ 25.000     $ 0            $25.000         |
| [Registrar abono] [Ver historial]                                 |
+-------------------------------------------------------------------+
~~~~

**Objetivo:** cada cliente tendrá un identificador generado automáticamente. Los datos obligatorios serán nombre, teléfono y dirección. El historial permitirá consultar el origen de la deuda y los abonos registrados.

## 5. Pedidos de mercancía

~~~~text
+-------------------------------------------------------------------+
| PEDIDOS DE MERCANCÍA                                              |
+-------------------------------------------------------------------+
| [Nuevo pedido]   Estado: [Todos v]   Proveedor: [___________]      |
+-------------------------------------------------------------------+
| Pedido    Fecha       Estado                Fecha esperada         |
| P-0001    --/--/----  Solicitado            --/--/----             |
+-------------------------------------------------------------------+
| Detalle del pedido                                                |
| Referencia        Canastas solicitadas   Recibidas   Pendientes    |
| Poker 250 ml             4                    2           2        |
| [Registrar recepción] [Ver pedido]                                |
+-------------------------------------------------------------------+
~~~~

**Regla:** un pedido en estado planeado o solicitado no altera el stock. Una recepción parcial aumenta el inventario por la cantidad que realmente llegó.

## 6. Movimientos de caja

~~~~text
+-------------------------------------------------------------------+
| MOVIMIENTOS DE CAJA                                               |
+-------------------------------------------------------------------+
| Tipo: [Ingreso / Egreso v]     Categoría: [Seleccionar v]          |
| Valor: [$________]            Fecha: [__/__/____]                 |
| Descripción: [_______________________________________________]    |
| [Registrar movimiento]                                            |
+-------------------------------------------------------------------+
| Movimientos del periodo                                           |
| Fecha       Tipo       Categoría              Valor               |
| --/--/----  Ingreso    Abono de cliente       $ 10.000            |
| --/--/----  Egreso     Compra de hielo        $  5.000            |
+-------------------------------------------------------------------+
~~~~

Categorías iniciales propuestas: cobro de venta, abono de cliente, otro ingreso, pago de mercancía, compra de hielo, servicios, pago a trabajadores, retiro de administrador y otro egreso.

## 7. Decisiones de usabilidad

- Los textos visibles estarán en español.
- El usuario recibirá mensajes claros de validación y confirmación.
- Los valores monetarios se mostrarán en COP.
- Los formularios mostrarán el saldo y las cantidades resultantes antes de confirmar operaciones importantes.
- Las operaciones sensibles, como registrar una venta o una recepción, pedirán confirmación antes de aplicar cambios.
- Estos wireframes se revisarán con el equipo y se ajustarán antes de construir las pantallas JavaFX.
