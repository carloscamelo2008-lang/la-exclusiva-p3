# Tabla de tareas de La Exclusiva

**Estado:** tabla viva de seguimiento. Actualizarla después de cada revisión del equipo y cada integración en GitHub.

| ID | Actividad | Responsable inicial | Prioridad | Estado | Criterio de cierre |
|---|---|---|---|---|---|
| T-01 | Revisar y aprobar configuración inicial | Jesús y Daniel; coordina Carlos | P0 | En revisión | Ambos aprueban el PR inicial y Maven pasa antes del merge a develop. |
| T-02 | Confirmar catálogo, presentaciones y precios | Equipo, lidera Jesús | P0 | En revisión | Catálogo, capacidades y tarifas comunicadas quedan reflejados correctamente. |
| T-03 | Definir clases compartidas y contratos de servicios | Equipo, coordina Carlos | P0 | En progreso | Firmas de productos, ventas, pagos y pedidos acordadas antes de programar en paralelo. |
| T-04 | Implementar catálogo y productos | Jesús | P0 | Pendiente | Crear y consultar referencias con capacidad y tarifas explícitas. |
| T-05 | Implementar inventario combinado | Jesús | P0 | Pendiente | Gestionar canastas completas y unidades sueltas y calcular unidades equivalentes. |
| T-06 | Implementar tarifas por presentación | Jesús con Carlos | P0 | Pendiente | Menores de 750 ml: $2.500 unidad, $45.000 media, $85.000 completa; Poker 750 ml: $6.000, $45.000 y $90.000. |
| T-07 | Implementar ventas con varias referencias | Carlos | P0 | Pendiente | Calcular cada línea y el total con la tarifa configurada. |
| T-08 | Integrar ventas con inventario | Carlos con Jesús | P0 | Pendiente | Descontar 1, 19/15/8 o 38/30/16 unidades según la presentación y la capacidad. |
| T-09 | Implementar clientes con ID automático | Daniel | P0 | Pendiente | ID único y nombre, teléfono y dirección obligatorios. |
| T-10 | Implementar registro de vendedores | Daniel | P0 | Pendiente | Nombre, teléfono y dirección se validan. |
| T-11 | Implementar pagos parciales, fiados, abonos y saldos | Daniel con Carlos | P0 | Pendiente | Total, pagado y saldo son coherentes; historial de abonos disponible. |
| T-12 | Crear módulo separado de pedidos de mercancía | Jesús | P1 | Pendiente | Pedido con referencias, cantidades, fecha y estado. |
| T-13 | Implementar recepción parcial/completa | Jesús con Carlos | P1 | Pendiente | Stock aumenta solo por cantidades realmente recibidas. |
| T-14 | Registrar ingresos y egresos con categorías | Carlos | P1 | Pendiente | Cada movimiento guarda fecha, valor, tipo, categoría y descripción. |
| T-15 | Consultar resumen de caja | Carlos | P1 | Pendiente | Diferenciar ventas, cobros, abonos, egresos y retiros. |
| T-16 | Diseñar el control futuro de envases retornables | Jesús con el equipo | P2 | Pospuesto | Modelo por tipo de envase, incluidos los compartidos. |
| T-17 | Diseñar el control de cavas físicas prestadas | Equipo, coordina Carlos | P2 | Pospuesto | Definir estado, ubicación, responsable e historial, separado de la media canasta comercial. |
| T-18 | Crear mockups visuales de JavaFX | Carlos con revisión del equipo | P1 | Pendiente | Diseños visuales revisados para ventas, inventario, clientes/abonos, pedidos y resumen diario. |
| T-19 | Revisar diagramas de paquetes y clases | Equipo, coordina Carlos | P0 | En revisión | Clases, tipos, responsabilidades, atributos, métodos y relaciones acordados. |
| T-20 | Justificar GRASP y SOLID | Cada integrante; consolida Carlos | P0 | En revisión | Cada justificación referencia clase o método concreto y se contrasta con el código. |
| T-21 | Mantener bitácoras de IA | Cada integrante | P0 | En progreso | Cada miembro registra interacciones reales, decisiones y verificaciones propias. |
| T-22 | Integrar PostgreSQL mediante JDBC | Equipo, coordina Carlos | P2 | Pospuesto | Esquema, repositorios/DAO, CRUD, excepciones y pruebas de persistencia. |
| T-23 | Implementar interfaz gráfica JavaFX | Equipo | P2 | Pospuesto | Interfaz funcional que reutiliza los servicios. |
| T-24 | Completar documentación de fase 4 | Equipo, consolida Carlos | P2 | Pendiente | Diagramas finales, diccionario de datos, trazabilidad y reporte Git con evidencias reales. |

## Estados permitidos

- **Pendiente:** no se ha comenzado.
- **En progreso:** hay trabajo activo.
- **En revisión:** requiere revisión o aprobación.
- **Completada:** cumple su criterio, fue probada y quedó integrada en develop.
- **Bloqueada:** depende de otra decisión o tarea.
- **Pospuesto:** se realizará en una etapa posterior.

No se marca una tarea como completada solo por tener código o documentación. Debe cumplir su criterio de aceptación y pasar la revisión acordada.
