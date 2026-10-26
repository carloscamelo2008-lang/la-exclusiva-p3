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

## 3. Control combinado del inventario

El inventario debe representar una combinación de canastas completas y unidades sueltas por referencia. Para una referencia determinada:

- **Canastas completas:** cantidad de canastas cerradas disponibles.
- **Unidades sueltas:** envases o latas que no forman una canasta completa.
- **Unidades totales equivalentes:** canastas completas multiplicadas por la capacidad de la referencia, más las unidades sueltas.

La capacidad por canasta depende de la presentación: 38 unidades para envases menores de 330 ml, 30 para presentaciones de 330 ml y 16 para la presentación de 750 ml. La aplicación deberá validar cualquier equivalencia adicional antes de registrarla.

Cuando una venta se hace por unidad, media canasta o canasta completa, el sistema debe calcular cuántas unidades se descuentan. No debe permitir que el stock equivalente resulte negativo.

## 4. Precios comunicados por el negocio

Los precios informados inicialmente son:

| Presentación de venta | Precio comunicado |
|---|---:|
| Unidad de referencia menor de 750 ml | $2.500 COP |
| Unidad de referencia de 750 ml | $6.000 COP |
| Media canasta/cava de presentaciones habituales | $45.000 COP |
| Canasta/cava completa de presentaciones habituales | $85.000 COP |
| Media canasta/cava de la referencia de 750 ml | $45.000 COP |
| Canasta/cava completa de la referencia de 750 ml | $90.000 COP |

Estos precios deben almacenarse de forma explícita por producto y presentación. No se deben calcular automáticamente a partir del precio unitario, porque los importes comunicados para unidades y canastas no necesariamente coinciden con la multiplicación aritmética.

**Pendiente de validación:** confirmar si el precio unitario de $6.000 corresponde exactamente a Poker 750 ml, si todos los productos menores de 750 ml usan las tarifas indicadas y si “media cava/cava completa” se refiere a la presentación comercial de venta. En el modelo se distinguirá la presentación comercial de venta de la cava física que se presta al cliente.

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

Se desarrollará una aplicación Java con arquitectura por capas y menú de consola para el avance inmediato. La primera prioridad es que los módulos principales funcionen y se puedan demostrar; la interfaz gráfica JavaFX y la integración definitiva con PostgreSQL se abordarán en una etapa posterior.

La aplicación se organizará en model, persistence, service y ui. Las reglas del negocio y los cálculos no deben estar concentrados en el menú. Los repositorios aislarán el mecanismo de persistencia para facilitar el paso posterior a PostgreSQL.

## 12. Decisiones por validar

1. Confirmar las tarifas unitarias y por presentación para cada referencia.
2. Confirmar qué se entiende por media cava/canasta y cómo se representa comercialmente.
3. Validar qué ocurre al intentar registrar un abono superior al saldo.
4. Definir el mínimo necesario para registrar proveedor y pedido.
5. Acordar qué mecanismo temporal de persistencia se utilizará en el avance inmediato antes de PostgreSQL.
6. Confirmar la prioridad de los módulos complementarios después de ventas, inventario y deudas.

## 13. Criterio de cierre

El análisis estará validado cuando el equipo confirme los precios y equivalencias, los flujos de ventas y abonos, y la prioridad de los módulos. Después se revisará el diagrama de clases y se convertirán las reglas de negocio en pruebas verificables.
