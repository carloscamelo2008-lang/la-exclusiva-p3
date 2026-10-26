# Requerimientos iniciales

**Estado:** borrador para revisión del equipo.  
**Prioridades:** P0 = primera demostración; P1 = después de estabilizar el núcleo; P2 = ampliación para la entrega final.

## 1. Requerimientos funcionales

| ID | Prioridad | Requerimiento | Criterio de aceptación |
|---|---|---|---|
| RF-01 | P0 | Registrar productos y presentaciones. | El usuario puede registrar una referencia con identificador, nombre, presentación, precio y los datos de inventario acordados. |
| RF-02 | P0 | Consultar productos y existencias. | El sistema muestra las referencias registradas y las cantidades disponibles. |
| RF-03 | P0 | Registrar ventas con varias líneas. | Una venta permite incluir más de una referencia y las cantidades correspondientes. |
| RF-04 | P0 | Calcular el total de una venta. | El total coincide con la suma de las líneas según los precios y cantidades definidos. |
| RF-05 | P0 | Actualizar el inventario al vender. | Al registrar una venta válida se descuentan las cantidades; si no hay stock suficiente se rechaza sin dejar cambios parciales. |
| RF-06 | P0 | Registrar y consultar clientes. | Se pueden guardar los datos mínimos acordados y consultar clientes existentes. |
| RF-07 | P0 | Registrar ventas a crédito. | Cuando una venta queda pendiente, el sistema registra el valor y el cliente asociado. |
| RF-08 | P0 | Registrar abonos y consultar saldos. | Cada abono queda asociado a una deuda, se conserva su historial y se actualiza el saldo. |
| RF-09 | P1 | Registrar ingresos y egresos. | Cada movimiento guarda fecha, valor, categoría y descripción. |
| RF-10 | P1 | Consultar resumen diario. | El sistema diferencia ventas registradas, dinero recibido, abonos y egresos anotados. |
| RF-11 | P1 | Registrar compras de mercancía. | Una compra registra productos, cantidades y valores, y actualiza el inventario de acuerdo con las reglas acordadas. |
| RF-12 | P2 | Controlar proveedores y pagos pendientes. | Se puede consultar el valor de compra, los pagos y el saldo pendiente por proveedor. |
| RF-13 | P2 | Controlar envases retornables. | El sistema identifica tipos de envase y conserva los movimientos acordados de entrega y devolución. |
| RF-14 | P2 | Registrar cavas prestadas. | Cada cava se identifica y permite consultar su estado, ubicación y responsable cuando esté prestada. |
| RF-15 | P2 | Registrar trabajadores y pagos. | Se registra el trabajador, el pago y la fecha correspondiente. |
| RF-16 | P2 | Incorporar una interfaz gráfica. | La interfaz final se desarrolla con JavaFX después de validar el núcleo funcional de consola. |

## 2. Requerimientos no funcionales

| ID | Requerimiento | Criterio de aceptación |
|---|---|---|
| RNF-01 | Lenguaje y versión. | El código debe compilar con Java 17. |
| RNF-02 | Construcción del proyecto. | Maven gestiona dependencias y permite compilar el proyecto en IntelliJ IDEA y NetBeans. |
| RNF-03 | Persistencia. | Los datos funcionales se almacenan en PostgreSQL y permanecen disponibles al reiniciar la aplicación. |
| RNF-04 | Usabilidad. | Los menús, mensajes, validaciones y reportes que vea el usuario están en español. |
| RNF-05 | Mantenibilidad. | El código se organiza en las capas acordadas: model, persistence, service y ui. |
| RNF-06 | Validación. | No se aceptan cantidades inválidas, valores negativos ni operaciones que incumplan reglas de negocio. |
| RNF-07 | Manejo de errores. | Los errores esperados se informan con mensajes comprensibles; no se muestran trazas técnicas al usuario. |
| RNF-08 | Seguridad de configuración. | Las credenciales de PostgreSQL no se guardan en archivos versionados ni en commits. |
| RNF-09 | Control de versiones. | Los cambios funcionales se integran mediante Pull Requests revisados hacia develop. |
| RNF-10 | Documentación. | Los documentos, comentarios y JavaDoc se redactan en español; los identificadores del código se escriben en inglés. |

## 3. Reglas críticas a probar

- Una venta no se registra si no contiene al menos una línea.
- No se vende una cantidad superior a la existencia disponible.
- El inventario no cambia si la venta es rechazada.
- El total de la venta se calcula con los precios y cantidades confirmados.
- Una venta fiada queda asociada al cliente y al saldo pendiente.
- Un abono no puede superar el saldo pendiente, salvo que el equipo acuerde explícitamente cómo manejar excedentes.
- El dinero recibido debe distinguirse del importe vendido cuando hay crédito.
- Los datos siguen disponibles después de cerrar y volver a abrir la aplicación.

## 4. Fuera del primer corte de entrega

La interfaz JavaFX, el control completo de cavas y envases, la nómina, y los créditos de proveedores se consideran ampliaciones posteriores, salvo que el equipo termine antes las funcionalidades P0 y confirme que puede asumir alguna de ellas sin comprometer la integración.
