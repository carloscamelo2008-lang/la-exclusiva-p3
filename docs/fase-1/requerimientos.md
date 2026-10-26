# Requerimientos iniciales de La Exclusiva

**Estado:** borrador actualizado con información del negocio.  
**Prioridad:** P0 = núcleo funcional del avance actual; P1 = siguiente bloque tras estabilizar el núcleo; P2 = ampliación posterior.  
**Nota:** la prioridad y el estado se deben actualizar durante el desarrollo.

## 1. Requerimientos funcionales

| ID | Prioridad | Requerimiento | Criterio de aceptación |
|---|---|---|---|
| RF-01 | P0 | Registrar productos y presentaciones. | Se registra una referencia con nombre, marca, volumen, capacidad por canasta y precios por presentación. |
| RF-02 | P0 | Generar identificadores de clientes. | El identificador se asigna automáticamente y no se repite. |
| RF-03 | P0 | Registrar clientes y vendedores. | Nombre, teléfono y dirección son obligatorios. |
| RF-04 | P0 | Consultar el catálogo y existencias. | Se muestra el catálogo con canastas completas, unidades sueltas y unidades equivalentes. |
| RF-05 | P0 | Registrar una venta con varias líneas. | Una venta permite mezclar referencias y presentaciones en una misma operación. |
| RF-06 | P0 | Calcular el total de la venta. | El sistema utiliza el precio definido para la presentación seleccionada y suma sus líneas. |
| RF-07 | P0 | Actualizar inventario al vender. | Las unidades vendidas se descuentan del stock y una venta con stock insuficiente se rechaza sin dejar cambios parciales. |
| RF-08 | P0 | Registrar pagos parciales. | Una venta puede tener pagos iniciales o parciales y el sistema muestra total, pagado y saldo. |
| RF-09 | P0 | Registrar abonos de clientes. | Cada abono se guarda con fecha y valor, se asocia a la cuenta correcta y reduce el saldo pendiente. |
| RF-10 | P0 | Consultar cuentas por cobrar. | Se puede consultar por cliente el total pendiente y el historial de abonos. |
| RF-11 | P1 | Registrar pedidos de mercancía por separado de las ventas. | Un pedido permite listar productos, cantidades esperadas, fecha y estado. |
| RF-12 | P1 | Registrar la recepción de mercancía. | La recepción parcial o completa actualiza el stock solo por las cantidades que llegaron realmente. |
| RF-13 | P1 | Registrar movimientos de ingresos y egresos. | Cada movimiento conserva fecha, valor, categoría y descripción. |
| RF-14 | P1 | Consultar resumen diario de caja. | Se diferencian ventas, dinero recibido, abonos, egresos y retiros. |
| RF-15 | P1 | Configurar precios por presentación. | La unidad, media canasta y canasta completa usan los precios definidos para el producto, sin inferirlos mediante multiplicación. |
| RF-16 | P2 | Controlar envases retornables. | Los envases vacíos se agrupan por tipo; Poker 250 ml y Aguilita 250 ml comparten tipo. |
| RF-17 | P2 | Controlar cavas físicas prestadas. | Cada cava puede identificarse y consultar estado, ubicación y responsable mientras esté prestada. |
| RF-18 | P2 | Registrar proveedores y pagos. | Se puede consultar el pedido, las cantidades recibidas y el estado de pago. |
| RF-19 | P2 | Registrar pagos de trabajadores y retiros administrativos. | Cada movimiento se registra con fecha, valor, categoría y descripción. |
| RF-20 | P2 | Incorporar interfaz gráfica JavaFX. | La interfaz gráfica se implementará para la entrega final, después de estabilizar la versión de consola. |

## 2. Reglas de inventario

| Regla | Descripción |
|---|---|
| RN-INV-01 | Las referencias menores de 330 ml utilizan 38 unidades por canasta, según el catálogo comunicado. |
| RN-INV-02 | Las referencias de 330 ml utilizan 30 unidades por canasta. |
| RN-INV-03 | La referencia de 750 ml utiliza 16 unidades por canasta. |
| RN-INV-04 | El stock se representa por canastas completas y unidades sueltas por referencia. |
| RN-INV-05 | Las unidades equivalentes se calculan como canastas completas × capacidad más unidades sueltas. |
| RN-INV-06 | No se permiten ventas que ocasionen existencias negativas. |
| RN-INV-07 | Poker 250 ml y Aguilita 250 ml comparten tipo de envase retornable. Cola y Pola en lata es no retornable. |
| RN-INV-08 | El registro de pedido no aumenta el stock; solo lo hace la recepción efectiva. |

## 3. Reglas de precios provisionales

| Producto / presentación | Precio preliminar |
|---|---:|
| Unidad menor de 750 ml | $2.500 COP |
| Unidad de 750 ml | $6.000 COP |
| Media canasta/cava de referencias habituales | $45.000 COP |
| Canasta/cava completa de referencias habituales | $85.000 COP |
| Media canasta/cava de 750 ml | $45.000 COP |
| Canasta/cava completa de 750 ml | $90.000 COP |

Los precios deberán confirmarse por referencia antes de finalizar el cálculo de ventas. No se derivan automáticamente de la multiplicación del precio unitario.

## 4. Reglas de pagos y cuentas por cobrar

- El total vendido, el dinero recibido y el saldo pendiente son valores distintos.
- Cada pago o abono debe quedar registrado de forma individual.
- Un abono reduce el saldo pendiente de la deuda a la que está asociado.
- El sistema no debe registrar un abono como una nueva venta.
- Los cobros reales pueden reflejarse como entradas de caja; la parte fiada no cuenta como efectivo recibido.
- No se aceptará un pago que haga negativo el saldo pendiente, salvo que el equipo defina expresamente cómo manejar pagos en exceso.

## 5. Categorías de movimientos de dinero

Las categorías son etiquetas para clasificar el motivo del movimiento y facilitar los reportes.

**Ingresos propuestos:** cobro de venta, abono de cliente, otro ingreso.

**Egresos propuestos:** pago de mercancía, compra de hielo, servicios, pago a trabajadores, retiro de administrador, otro egreso.

Las listas se pueden ampliar durante el desarrollo, pero sus valores deben ser consistentes en toda la aplicación.

## 6. Requerimientos no funcionales

| ID | Requerimiento | Criterio de aceptación |
|---|---|---|
| RNF-01 | Versión de Java. | El código compila con Java 17. |
| RNF-02 | Construcción común. | Maven permite construir el proyecto tanto en IntelliJ IDEA como en NetBeans. |
| RNF-03 | Interfaz actual. | Para el avance inmediato, las operaciones están disponibles mediante consola. |
| RNF-04 | Interfaz final. | JavaFX queda planificado para la entrega final. |
| RNF-05 | Idioma. | Documentación, comentarios, JavaDoc y mensajes visibles al usuario están en español. Los identificadores técnicos del código están en inglés. |
| RNF-06 | Mantenibilidad. | Se mantiene la separación model, persistence, service y ui. |
| RNF-07 | Validación. | El sistema valida datos obligatorios, cantidades, precios y reglas de negocio antes de aplicar cambios. |
| RNF-08 | Manejo de errores. | Los errores esperados se muestran mediante mensajes comprensibles en español. |
| RNF-09 | Control de versiones. | Las funcionalidades se integran mediante Pull Requests revisados hacia develop. |
| RNF-10 | Credenciales. | No se guardan contraseñas ni archivos de configuración local con secretos en el repositorio. |
| RNF-11 | Persistencia definitiva. | PostgreSQL y JDBC se implementarán en la siguiente etapa; no se afirmará que están integrados hasta que existan código y pruebas. |
| RNF-12 | Calidad de diseño. | Se documentan y justifican los patrones GRASP y los principios SOLID aplicados realmente en el código. |

## 7. Criterios de prueba para el núcleo

- Una venta con múltiples referencias calcula el total correcto.
- Una venta por unidad, media canasta o canasta completa descuenta las unidades equivalentes correctas.
- Una venta con stock insuficiente no cambia el inventario.
- Una venta parcialmente pagada muestra correctamente total, pagado y saldo.
- Un abono reduce únicamente el saldo de la cuenta correspondiente.
- No se permite que un saldo quede negativo accidentalmente.
- Poker 250 ml y Aguilita 250 ml se identifican con el tipo de envase compartido.
- Un pedido planeado no aumenta el inventario; la recepción sí.
- Los datos obligatorios de clientes y vendedores se validan.
- Los mensajes visibles se muestran en español.

## 8. Alcance fuera del núcleo inicial

La interfaz JavaFX y la implementación completa de PostgreSQL se abordarán en la siguiente etapa. El control detallado de envases, cavas, proveedores y pagos a trabajadores se priorizará después de integrar ventas, inventario y cuentas por cobrar.
