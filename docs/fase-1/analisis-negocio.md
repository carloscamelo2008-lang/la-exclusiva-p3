# Análisis del negocio: La Exclusiva

**Proyecto:** Sistema de administración para La Exclusiva  
**Asignatura:** Programación de Computadores III  
**Versión:** 0.2  
**Estado:** Borrador basado en información del negocio; pendiente de validación del equipo.

## 1. Propósito

Este documento recoge el contexto conocido del negocio familiar La Exclusiva, identifica dificultades de sus procesos actuales y define reglas preliminares para diseñar el sistema. Servirá como base para los requerimientos, los diagramas, el desarrollo y las pruebas.

## 2. Contexto del negocio

La Exclusiva es un negocio familiar dedicado a la venta de cervezas de diferentes marcas y presentaciones. Sus operaciones habituales incluyen recibir mercancía, mantener existencias, atender pedidos, registrar ventas, cobrar a clientes, registrar pagos parciales y abonos, realizar compras y controlar los movimientos de dinero.

### Catálogo inicial conocido

| Referencia | Presentación | Unidades por canasta completa | Tipo de envase |
|---|---:|---:|---|
| Costeñita | 175 ml | 38 | Retornable, exclusivo |
| Poker | 250 ml | 38 | Retornable, compartido con Aguilita 250 ml |
| Aguilita | 250 ml | 38 | Retornable, compartido con Poker 250 ml |
| Costeña Bacana | 330 ml | 30 | Retornable, exclusivo |
| Cola y Pola | Lata de 330 ml | 30 | Lata no retornable |
| Poker | 750 ml | 16 | Retornable, tipo exclusivo |

El catálogo puede ampliarse según la demanda. Las cantidades anteriores corresponden a la información reportada por el negocio y deben utilizarse como datos iniciales configurables, no como reglas imposibles de cambiar.

## 3. Inventario combinado

El inventario se lleva por combinación de canastas completas y unidades sueltas para cada referencia.

- **Canastas completas:** cantidad de canastas sin fraccionar.
- **Unidades sueltas:** unidades disponibles fuera de una canasta completa.
- **Unidades equivalentes:** canastas completas multiplicadas por su capacidad, más las unidades sueltas.

Las referencias menores de 330 ml usan 38 unidades por canasta; las presentaciones de 330 ml usan 30; Poker de 750 ml usa 16. Una media canasta equivale a la mitad de la capacidad: 19, 15 u 8 unidades respectivamente.

Al vender por unidad, media canasta o canasta completa, el sistema convierte la presentación a unidades equivalentes y descuenta la cantidad correcta. No debe aceptar una operación que deje existencias negativas.

## 4. Precios comunicados por el negocio

| Producto o presentación de venta | Precio |
|---|---:|
| Una unidad de cualquier cerveza menor de 750 ml | $2.500 COP |
| Una unidad de Poker de 750 ml | $6.000 COP |
| Media canasta de cualquier referencia menor de 750 ml | $45.000 COP |
| Canasta completa de cualquier referencia menor de 750 ml | $85.000 COP |
| Media canasta de Poker de 750 ml | $45.000 COP |
| Canasta completa de Poker de 750 ml | $90.000 COP |

El precio unitario de $2.500 aplica a Costeñita 175 ml, Poker 250 ml, Aguilita 250 ml, Costeña Bacana 330 ml y Cola y Pola en lata de 330 ml, por ser referencias menores de 750 ml. Poker de 750 ml tiene precio unitario de $6.000.

Los precios por media canasta y canasta completa se configuran de forma explícita; no se calculan multiplicando el precio unitario por el número de unidades. En estas reglas comerciales, “media” significa media canasta, no la cava física que pueda prestarse temporalmente al cliente.

## 5. Envases retornables y cavas físicas

Los envases de Poker 250 ml y Aguilita 250 ml pertenecen al mismo tipo compartido. Costeñita 175 ml, Costeña Bacana 330 ml y Poker 750 ml tienen tipos de envase retornable distintos. Cola y Pola se comercializa en lata no retornable.

El sistema deberá distinguir entre:

1. **Inventario de producto:** cerveza por canastas y unidades sueltas.
2. **Inventario de envases vacíos:** unidades retornables agrupadas por tipo de envase.
3. **Cavas físicas prestadas:** recipientes identificables que se entregan temporalmente y deben poder localizarse y recuperarse.

La venta de cerveza no debe confundirse con el préstamo de una cava física. El control detallado de envases y cavas se desarrollará después de estabilizar ventas e inventario.

## 6. Ventas, pagos parciales y deudas

Una venta puede incluir distintas referencias y presentaciones. El sistema debe registrar las líneas de venta, sus cantidades y los precios aplicados.

Los clientes pueden pagar parcialmente. Por ello, el sistema debe diferenciar:

- **Total de la venta:** valor completo de los productos vendidos.
- **Total abonado/pagado:** suma de los pagos registrados para esa venta.
- **Saldo pendiente:** total de la venta menos los pagos registrados.

Cada pago o abono debe conservar su fecha, valor y relación con la venta o cuenta que está pagando. Un abono reduce el saldo pendiente y no debe crear una segunda venta ni duplicar el ingreso de dinero. El sistema debe impedir que el saldo se vuelva negativo, salvo que en el futuro se defina explícitamente una regla para pagos en exceso.

## 7. Clientes y vendedores

Los datos obligatorios identificados para clientes y vendedores son:

- Nombre.
- Número de teléfono.
- Dirección.

El sistema debe generar automáticamente un identificador para cada cliente, sin que el usuario tenga que inventar o escribir manualmente ese identificador. El mecanismo concreto y su formato se definirán durante el diseño técnico. Se recomienda usar el mismo criterio de identificador generado para vendedores.

## 8. Compras y pedidos de mercancía

La compra de mercancía será un módulo separado de la venta al cliente. El propósito es registrar el próximo pedido, los productos y cantidades que se esperan recibir y el estado del pedido.

Se propone manejar estos estados iniciales:

- **Planeado:** el pedido se está preparando.
- **Solicitado:** el pedido ya se comunicó al proveedor.
- **Recibido parcialmente:** llegó una parte de la mercancía.
- **Recibido:** llegó toda la mercancía esperada.
- **Cancelado:** el pedido no se realizará.

El inventario solo debe aumentar por las cantidades efectivamente recibidas, no por el mero hecho de crear o solicitar un pedido. Los pagos al proveedor se registrarán aparte para distinguir la mercancía solicitada, la recibida y el dinero efectivamente pagado.

## 9. Ingresos y egresos: qué significan las categorías

Una categoría es simplemente una etiqueta que permite saber **por qué entró o salió dinero**. No es un módulo diferente ni un cálculo complicado. Por ejemplo, no es lo mismo gastar dinero en hielo que retirar dinero para el administrador.

Se proponen estas categorías iniciales.

**Ingresos:**
- Cobro de una venta.
- Abono de una deuda de cliente.
- Otro ingreso.

**Egresos:**
- Pago de una compra de mercancía.
- Compra de hielo.
- Servicios.
- Pago a trabajadores.
- Retiro de administrador.
- Otro egreso.

Cuando un cliente paga una parte de una venta, el dinero efectivamente recibido se registra como entrada; la porción que queda fiada sigue siendo una cuenta por cobrar, no efectivo en caja. Cuando se registra un pedido a un proveedor, todavía no debe anotarse como dinero gastado hasta que corresponda registrar el pago.

## 10. Situación actual y dificultades

Parte de la información se registra manualmente. Las deudas, por ejemplo, se anotan en un tablero con el nombre del cliente y el valor pendiente. Este método puede dificultar reconstruir los productos que originaron una deuda, la fecha de la operación, los abonos efectuados y el saldo correcto.

El manejo del dinero tampoco conserva de manera uniforme el detalle de todas las entradas, salidas y retiros. Asimismo, el seguimiento del inventario exige considerar presentaciones con distintas capacidades de canasta, unidades sueltas y tipos de envase retornable.

## 11. Solución propuesta

Se desarrollará una aplicación Java por capas y con menú de consola para el avance inmediato. Se priorizarán catálogo, inventario combinado, ventas, clientes y pagos parciales.

Para este avance se propone usar colecciones en memoria detrás de contratos de repositorio, de modo que la lógica de negocio no dependa del mecanismo temporal de almacenamiento. Los datos se reiniciarán al cerrar la aplicación. PostgreSQL y JDBC quedan para la etapa posterior; JavaFX será la interfaz gráfica de la entrega final. Los mockups visuales se diseñarán como una tarea posterior de la fase 2.

## 12. Decisiones por validar

**Aclaradas por el negocio:**
- Las unidades de todas las referencias menores de 750 ml cuestan $2.500 COP.
- La unidad de Poker 750 ml cuesta $6.000 COP.
- Media canasta equivale a la mitad de las unidades de una canasta completa.
- Los precios de media canasta y canasta completa se configuran independientemente del precio unitario.
- El inventario combina canastas completas y unidades sueltas.
- Se permiten pagos parciales; los abonos reducen el saldo pendiente.
- Los pedidos de mercancía van en un módulo separado.
- Nombre, teléfono y dirección son obligatorios para clientes y vendedores.

**Pendientes de decisión técnica o validación del equipo:**
1. Qué identificador visible se usará para cliente y vendedor.
2. Cómo se inicializarán los datos de prueba en la persistencia temporal en memoria.
3. Qué datos mínimos son obligatorios al crear un pedido y cómo registrar una recepción parcial.
4. Qué módulos complementarios entran después de ventas, inventario y cuentas por cobrar.
5. El diseño visual de los mockups de la futura interfaz JavaFX.

## 13. Criterio de cierre

El análisis estará validado cuando el equipo confirme los precios y equivalencias, los flujos de ventas y abonos, y la prioridad de los módulos. Después se revisará el diagrama de clases y se convertirán las reglas de negocio en pruebas verificables.
