# Tabla inicial de tareas

**Estado actual:** las funcionalidades están pendientes hasta que exista implementación, prueba y revisión en GitHub. La tabla debe actualizarse durante el desarrollo.

| ID | Actividad | Responsable inicial | Prioridad | Estado | Criterio de cierre |
|---|---|---|---|---|---|
| T-01 | Revisar y aprobar la configuración inicial del repositorio | Jesús y Daniel; coordina Carlos | P0 | En revisión | Ambos revisan el PR de configuración y aprueban la base antes del merge a develop. |
| T-02 | Validar precios, presentaciones y capacidades de canastas | Equipo, lidera Jesús | P0 | Pendiente | Se confirman las tarifas y equivalencias por referencia. |
| T-03 | Definir modelo de dominio y contratos entre módulos | Equipo, coordina Carlos | P0 | Pendiente | Clases, relaciones y firmas compartidas acordadas antes de codificar en paralelo. |
| T-04 | Implementar productos y catálogo inicial | Jesús | P0 | Pendiente | Se pueden registrar y consultar las referencias conocidas. |
| T-05 | Implementar inventario combinado: canastas y unidades sueltas | Jesús | P0 | Pendiente | Las unidades equivalentes se calculan y se validan correctamente. |
| T-06 | Registrar precios por unidad, media canasta y canasta completa | Jesús con Carlos | P0 | Pendiente | Los precios por presentación se configuran por referencia y se prueban. |
| T-07 | Registrar clientes con ID generado y datos obligatorios | Daniel | P0 | Pendiente | ID único generado; nombre, teléfono y dirección obligatorios. |
| T-08 | Registrar vendedores y datos básicos | Daniel | P0 | Pendiente | Nombre, teléfono y dirección se validan. |
| T-09 | Implementar ventas con varias referencias y presentaciones | Carlos | P0 | Pendiente | Una venta mixta calcula total y unidades a descontar correctamente. |
| T-10 | Integrar venta con inventario | Carlos con Jesús | P0 | Pendiente | El stock disminuye por las unidades equivalentes; la venta inválida no deja cambios parciales. |
| T-11 | Implementar pagos parciales, abonos y cuentas por cobrar | Daniel con Carlos | P0 | Pendiente | Total, pagado y saldo se actualizan y se conserva el historial de abonos. |
| T-12 | Crear módulo de pedidos de mercancía | Jesús | P1 | Pendiente | Se puede registrar un pedido y sus productos, cantidades, fecha y estado. |
| T-13 | Registrar recepción parcial o completa de pedidos | Jesús con Carlos | P1 | Pendiente | El stock aumenta solo por las cantidades realmente recibidas. |
| T-14 | Registrar ingresos y egresos con categorías | Carlos | P1 | Pendiente | Cada movimiento registra fecha, valor, categoría y descripción. |
| T-15 | Consultar resumen diario y movimientos de caja | Carlos | P1 | Pendiente | Se diferencian ventas, efectivo recibido, abonos, egresos y retiros. |
| T-16 | Documentar y diseñar control de envases retornables | Jesús con el equipo | P2 | Pendiente | El modelo distingue tipos de envase y la relación compartida Poker 250/Aguilita 250. |
| T-17 | Documentar y diseñar préstamos de cavas físicas | Equipo, coordina Carlos | P2 | Pendiente | Se acuerdan estado, ubicación, responsable e historial mínimo. |
| T-18 | Preparar mockups de la futura interfaz JavaFX | Carlos con revisión del equipo | P1 | En revisión | Wireframes de venta, inventario, clientes/deudas, pedidos y resumen diario revisados. |
| T-19 | Documentar arquitectura, diagrama de clases y paquetes | Equipo, coordina Carlos | P0 | En revisión | Los diagramas y responsabilidades quedan revisados antes de consolidar el diseño. |
| T-20 | Registrar la aplicación de GRASP y SOLID | Cada integrante; consolida Carlos | P0 | En revisión | Cada justificación referencia una clase o método real y se actualiza tras los cambios. |
| T-21 | Mantener las bitácoras de uso de IA | Cada integrante | P0 | En progreso | Cada persona registra su uso real de herramientas de IA sin inventar entradas. |
| T-22 | Integrar PostgreSQL mediante JDBC | Equipo, coordina Carlos | P2 | Pospuesto | Esquema, repositorios y pruebas de persistencia integradas en una etapa posterior. |
| T-23 | Implementar interfaz gráfica JavaFX | Equipo | P2 | Pospuesto | Interfaz gráfica probada en la entrega final. |

## Regla para actualizar estados

Usar únicamente: **Pendiente**, **En progreso**, **En revisión**, **Completada**, **Bloqueada** o **Pospuesto**.

Una actividad se marca como Completada cuando satisface su criterio de aceptación, se ha probado y su Pull Request está aprobado e integrado en develop.
