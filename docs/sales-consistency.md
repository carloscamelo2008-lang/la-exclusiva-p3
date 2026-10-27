# Consistencia de ventas y saldos

## Abonos de clientes

`CustomerService.applyPaymentToCustomer` conserva su uso para deudas generales, como las
registradas con `addDebtToCustomer`. Cuando una parte del saldo está asignada a ventas, un
abono general solo puede reducir la diferencia entre el saldo total del cliente y esa parte.
Si el importe solicitado alcanzaría la deuda de ventas, se rechaza. Los abonos de ventas deben
pasar por `SaleService.registerPayment`, que actualiza la venta y la asignación agregada por el
mismo importe.

El sistema no distribuye un abono general entre ventas: el código anterior no define qué venta
debería reducirse. La asignación de saldos de ventas se mantiene en memoria dentro de
`CustomerService`, igual que los repositorios actuales. No sobrevive a reiniciar el proceso ni
se puede reconstruir desde clientes ya cargados; una futura persistencia deberá guardar esa
asignación junto con las ventas y los clientes.

## Compensaciones

El registro de una venta valida sus datos y existencias antes de modificar estado. Si falla
después de iniciar cambios, intenta eliminar la venta, restaurar el saldo y la asignación del
cliente, reponer las existencias y quitar los movimientos nuevos. Los abonos hacen lo mismo con
el estado anterior de la venta y el saldo del cliente. Los repositorios en memoria incluidos
permiten esas compensaciones, incluidas fallas simuladas que ocurren después de escribir.

Esto no es atomicidad durable ni cubre una falla de la propia compensación. Si un repositorio
rechaza también la escritura inversa, el error original conserva la falla de rollback como
excepción suprimida y el estado podría quedar parcial. Tampoco hay aislamiento ante operaciones
concurrentes entre repositorios; el flujo actual es secuencial y en memoria.
