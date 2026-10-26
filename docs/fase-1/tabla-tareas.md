# Tabla inicial de tareas

**Estado inicial:** todas las actividades funcionales están pendientes hasta que exista evidencia de su implementación y revisión.

| ID | Actividad | Responsable inicial | Prioridad | Estado | Criterio de cierre |
|---|---|---|---|---|---|
| T-01 | Revisar y aprobar la configuración inicial del repositorio | Jesús y Daniel; coordinación de Carlos | P0 | En revisión | Ambos revisan el Pull Request inicial y dejan su aprobación. |
| T-02 | Validar las reglas de negocio para presentaciones y canastas | Equipo, lidera Jesús | P0 | Pendiente | Se documentan equivalencias y se resuelven las preguntas críticas. |
| T-03 | Definir el esquema inicial de PostgreSQL | Equipo, coordina Carlos | P0 | Pendiente | Tablas y relaciones mínimas aprobadas antes de programar consultas. |
| T-04 | Registrar y consultar productos | Jesús | P0 | Pendiente | Pruebas de creación, consulta y datos inválidos. |
| T-05 | Consultar y actualizar inventario | Jesús | P0 | Pendiente | Una venta reduce stock y no se aceptan existencias negativas. |
| T-06 | Registrar clientes | Daniel | P0 | Pendiente | Registro y consulta con identificador único según las reglas acordadas. |
| T-07 | Registrar cuentas por cobrar, abonos y saldos | Daniel | P0 | Pendiente | Una deuda y sus abonos producen el saldo esperado y conservan el historial. |
| T-08 | Integrar el registro de ventas | Carlos | P0 | Pendiente | Una venta con varias líneas calcula el total y actualiza inventario. |
| T-09 | Integrar ventas a crédito con clientes y abonos | Carlos con Daniel | P0 | Pendiente | El saldo de la deuda coincide con los abonos registrados. |
| T-10 | Registrar movimientos de ingresos y egresos | Carlos, sujeto a avance P0 | P1 | Pendiente | Los movimientos quedan guardados y se pueden consultar. |
| T-11 | Crear un resumen diario básico | Carlos, sujeto a avance P0 | P1 | Pendiente | Ventas, efectivo recibido, abonos y egresos aparecen diferenciados. |
| T-12 | Documentar y probar cavas, envases, proveedores y pagos a trabajadores | Equipo según la distribución posterior | P2 | Pendiente | Requerimientos detallados y priorizados para la versión final. |

## Seguimiento

Actualizar esta tabla en cada revisión de equipo. No cambiar un estado a “Completada” solo porque el código exista: debe cumplir el criterio de aceptación y estar revisado e integrado en develop.
