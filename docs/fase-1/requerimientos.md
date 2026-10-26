# Requerimientos iniciales de La Exclusiva

**Estado:** borrador actualizado con las reglas comunicadas por el negocio.  
**Prioridades:** P0 = núcleo del primer avance; P1 = complemento después de estabilizar el núcleo; P2 = etapa posterior.  
**Seguimiento:** actualizar estado y evidencia durante el desarrollo.

## 1. Requerimientos funcionales

| ID | Prioridad | Requerimiento | Criterio de aceptación |
|---|---|---|---|
| RF-01 | P0 | Registrar productos y presentaciones. | Cada referencia conserva nombre, marca, volumen, unidades por canasta y precios explícitos por presentación. |
| RF-02 | P0 | Generar ID de cliente automáticamente. | El identificador se genera sin entrada manual y no se repite. |
| RF-03 | P0 | Registrar clientes y vendedores. | Nombre, teléfono y dirección son obligatorios. |
| RF-04 | P0 | Consultar catálogo e inventario. | Se muestran canastas completas, unidades sueltas y total equivalente por referencia. |
| RF-05 | P0 | Registrar venta con varias líneas. | Una operación puede mezclar varias referencias y presentaciones. |
| RF-06 | P0 | Calcular total de venta. | Cada línea usa el precio configurado para la presentación elegida y el total suma todas las líneas. |
| RF-07 | P0 | Descontar inventario al vender. | Se descuentan las unidades equivalentes correctas y se rechaza una venta con existencias insuficientes. |
| RF-08 | P0 | Registrar pagos parciales. | La venta conserva total, valor pagado y saldo pendiente. |
| RF-09 | P0 | Registrar abonos. | El abono se asocia a la cuenta correcta, reduce el saldo y conserva fecha e importe. |
| RF-10 | P0 | Consultar cuentas por cobrar. | Se puede consultar por cliente ventas pendientes, abonos e importe adeudado. |
| RF-11 | P1 | Registrar pedidos de mercancía por separado. | Se registra pedido, referencias, cantidades solicitadas, fecha y estado. |
| RF-12 | P1 | Registrar recepción de mercancía. | Solo lo que llega realmente aumenta el inventario; se admite recepción parcial. |
| RF-13 | P1 | Registrar ingresos y egresos. | Cada movimiento conserva fecha, valor, tipo, categoría y descripción. |
| RF-14 | P1 | Consultar resumen de caja. | Se diferencian ventas, dinero recibido, abonos, egresos y retiros. |
| RF-15 | P1 | Configurar precios por presentación. | Unidad, media canasta y canasta completa usan sus tarifas explícitas por referencia. |
| RF-16 | P2 | Controlar envases retornables. | Los envases se agrupan por tipo; Poker 250 ml y Aguilita 250 ml comparten tipo. |
| RF-17 | P2 | Controlar cavas físicas prestadas. | Se identifica cada cava y se consulta su estado, ubicación y responsable. |
| RF-18 | P2 | Registrar proveedores y pagos. | Se consulta pedido, recepción de mercancía y estado de pago. |
| RF-19 | P2 | Registrar pagos a trabajadores y retiros. | Cada movimiento tiene fecha, importe, categoría y descripción. |
| RF-20 | P2 | Implementar interfaz JavaFX. | La interfaz final reutiliza los servicios del sistema. |
| RF-21 | P2 | Integrar PostgreSQL/JDBC. | El esquema, repositorios, CRUD y transacciones se implementan y prueban en la etapa de persistencia definitiva. |

## 2. Reglas de inventario

| ID | Regla |
|---|---|
| RN-INV-01 | Las referencias menores de 330 ml usan 38 unidades por canasta. |
| RN-INV-02 | Las presentaciones de 330 ml usan 30 unidades por canasta. |
| RN-INV-03 | Poker de 750 ml usa 16 unidades por canasta. |
| RN-INV-04 | El inventario combina canastas completas y unidades sueltas por referencia. |
| RN-INV-05 | Las unidades equivalentes son canastas completas por capacidad más unidades sueltas. |
| RN-INV-06 | Media canasta equivale a 19, 15 u 8 unidades cuando la canasta contiene 38, 30 o 16 unidades respectivamente. |
| RN-INV-07 | Una venta no puede generar existencias negativas. |
| RN-INV-08 | Poker 250 ml y Aguilita 250 ml comparten envase retornable; Cola y Pola en lata no es retornable. |
| RN-INV-09 | Crear un pedido no aumenta el inventario; la recepción real sí. |

## 3. Tarifas comunicadas

| Presentación | Precio |
|---|---:|
| Unidad de cualquier cerveza menor de 750 ml | $2.500 COP |
| Unidad de Poker 750 ml | $6.000 COP |
| Media canasta de cualquier referencia menor de 750 ml | $45.000 COP |
| Canasta completa de cualquier referencia menor de 750 ml | $85.000 COP |
| Media canasta de Poker 750 ml | $45.000 COP |
| Canasta completa de Poker 750 ml | $90.000 COP |

No calcular el precio de la canasta multiplicando el precio unitario por la cantidad. Las tarifas por presentación se configuran de forma independiente. En estas reglas comerciales, media canasta significa la mitad de las unidades de la canasta, no una cava física prestada.

## 4. Reglas de pagos

- El total vendido, el dinero recibido y el saldo pendiente son valores distintos.
- Se permiten pagos parciales.
- Cada pago o abono se registra individualmente y se asocia a la cuenta correcta.
- Un abono reduce el saldo; no crea una venta nueva.
- La parte fiada no se registra como efectivo recibido.
- En la primera versión se rechaza un abono superior al saldo para evitar saldos negativos.

## 5. Categorías preliminares de caja

**Ingresos:** cobro de venta, abono de cliente, otro ingreso.

**Egresos:** pago de mercancía, compra de hielo, servicios, pago a trabajadores, retiro del administrador, otro egreso.

La categoría identifica por qué entró o salió el dinero. El movimiento se registra cuando el dinero se recibe o paga realmente.

## 6. Requerimientos no funcionales

| ID | Requerimiento | Criterio de aceptación |
|---|---|---|
| RNF-01 | Compilación | Java 17 y Maven construyen el proyecto. |
| RNF-02 | Compatibilidad | El proyecto se abre en IntelliJ IDEA y NetBeans. |
| RNF-03 | Interfaz inicial | El primer avance usa consola y mensajes en español. |
| RNF-04 | Interfaz final | JavaFX se aborda en la entrega final. |
| RNF-05 | Idioma | Documentos, comentarios, JavaDoc y textos al usuario están en español; identificadores de código, en inglés. |
| RNF-06 | Arquitectura | Se separan model, persistence, service y ui. |
| RNF-07 | Validación | Se validan datos obligatorios, cantidades, precios, pagos y stock antes de aplicar cambios. |
| RNF-08 | Excepciones | Los errores esperados se muestran en español sin exponer trazas internas al usuario. |
| RNF-09 | Control de versiones | Los cambios se integran mediante Pull Requests revisados. |
| RNF-10 | Secretos | No se suben contraseñas ni datos personales reales. |
| RNF-11 | Persistencia temporal | Las colecciones en memoria son temporales y se reinician al cerrar la aplicación. |
| RNF-12 | Persistencia definitiva | PostgreSQL/JDBC queda para una etapa posterior. |
| RNF-13 | Diseño | GRASP y SOLID se justifican con referencias concretas al código implementado. |

## 7. Pruebas críticas del núcleo

- Una unidad de referencia menor de 750 ml cuesta $2.500 COP.
- Una unidad de Poker 750 ml cuesta $6.000 COP.
- Media canasta descuenta 19, 15 u 8 unidades según la capacidad.
- Canasta completa descuenta 38, 30 o 16 unidades según la referencia.
- Una venta mixta calcula las líneas y descuenta cada referencia correctamente.
- Una venta con stock insuficiente no modifica parcialmente el inventario.
- Un pago parcial y un abono posterior actualizan total pagado y saldo.
- El ID de cliente se genera automáticamente y no se repite.
- Cliente y vendedor no se guardan si falta nombre, teléfono o dirección.
- Un pedido sin recibir no cambia el stock; una recepción parcial sí actualiza las cantidades recibidas.

## 8. Fuera de la implementación inicial

La integración definitiva con PostgreSQL, la interfaz JavaFX y el control detallado de envases/cavas se desarrollarán después del núcleo de consola.
