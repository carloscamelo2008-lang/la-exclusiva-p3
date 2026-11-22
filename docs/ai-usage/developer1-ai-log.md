# Bitácora de uso de IA - Desarrollador 1

**Integrante:** Jesús Daniel Díaz Álvarez  
**Rol inicial:** productos, inventario y pedidos de mercancía  
**Proyecto:** La Exclusiva

## Registro de interacciones

Registro retrospectivo preparado el 22 de noviembre de 2026 a partir de las consultas reales del módulo de compras, el historial Git y los reportes locales de pruebas. Las fechas corresponden a Colombia.

### Contrato técnico de compras y recepción

- **Fecha** — 2026-11-19.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Diseño de Compras y Recepción en `feature/jesus-orders-reception`.
- **Objetivo de la consulta** — Identificar las llaves foráneas, los contratos JDBC de inventario, las reglas de canastas, la autorización y el montaje JavaFX antes de implementar el módulo.
- **Resumen de la sugerencia recibida** — Usar las claves existentes de `beer_product`, `user_account` y `stock_movement`, reutilizar la conversión por producto y coordinar el aumento y el movimiento en la transacción compartida. Para vincular el movimiento, recuperar su ID con `saveAndReturnId(...)`.
- **Qué revisé, acepté, modifiqué o descarté** — Tomé esas firmas y restricciones como base para definir V004 y mantuve la pantalla de compras independiente de la navegación principal.
- **Evidencia de verificación** — Revisión de V001, V002, `database/jdbc-integration-contract.md` y las clases de dominio, inventario y JavaFX. La consulta fue de solo lectura.

### Revisión de identidad de Daniel

- **Fecha** — Consulta previa al primer microcommit de compras. El día exacto de la revisión no quedó confirmado en el registro disponible.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Revisión de seguridad para `feature/jesus-orders-reception` sobre `86fae23` de `origin/feature/daniel-identity-users`.
- **Objetivo de la consulta** — Auditar la revocación de sesiones, la protección del último administrador, el restablecimiento de contraseñas, la exclusividad de vendedor y el contrato que podría consumir Compras.
- **Resumen de la sugerencia recibida** — La versión cubría cambios de estado, rol, hash y vendedor. La revalidación disponible exigía `MANAGE_USERS`. El bloqueo del último administrador protegía a los escritores que seguían el protocolo. La revisión también señaló la falta de fortaleza mínima de contraseña, el riesgo de registrar detalles del parseo de hashes, la ausencia de un índice único parcial para vendedor activo y la discrepancia V104/V003.
- **Qué revisé, acepté, modifiqué o descarté** — Dejé la revisión como insumo de integración. Compras usa los permisos existentes de inventario y deja la FK de auditoría de usuario como opcional mientras la sesión pueda provenir de memoria.
- **Evidencia de verificación** — Revisión estática de `86fae23`, sus pruebas y la migración de versión de seguridad. Durante esa consulta no se ejecutaron pruebas ni se modificaron archivos.

### Modelo y esquema V004

- **Fecha** — 2026-11-21.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Primer microcommit de `feature/jesus-orders-reception`, `b628095`.
- **Objetivo de la consulta** — Crear la migración V004 y las seis entidades del ciclo de compras.
- **Resumen de la sugerencia recibida** — Definir cinco tablas con las llaves foráneas, restricciones e índices acordados. Conservar la capacidad histórica de canasta y validar conversiones, desbordamiento y sobrerrecepción en el modelo.
- **Qué revisé, acepté, modifiqué o descarté** — Publiqué la migración y las entidades como archivos nuevos del módulo.
- **Evidencia de verificación** — Diff de `b628095`, compilación con Java 17 y comprobaciones locales de cantidades negativas, totales inconsistentes, desbordamiento y sobrerrecepción. La migración no se aplicó durante esa consulta.

### Repositorios de compras

- **Fecha** — 2026-11-21.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Segundo microcommit de `feature/jesus-orders-reception`, `df1a7d3`.
- **Objetivo de la consulta** — Persistir proveedores, pedidos y recepciones en memoria y JDBC.
- **Resumen de la sugerencia recibida** — Compartir el proveedor JDBC, bloquear cabecera y líneas con `SELECT ... FOR UPDATE`, comprobar el saldo contra la instantánea vigente y exigir unicidad de la clave de recepción por pedido.
- **Qué revisé, acepté, modifiqué o descarté** — Incorporé los tres contratos y sus adaptadores. El flujo guarda el detalle de recepción antes de actualizar los acumulados del pedido.
- **Evidencia de verificación** — Diff de `df1a7d3`. Codex ejecutó nueve pruebas focalizadas en Java 17. Informé que la suite de esa etapa aprobó 277 de 277 pruebas.

### Servicios de pedidos y recepción

- **Fecha** — 2026-11-21.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Tercer microcommit de `feature/jesus-orders-reception`, `ac86ddd`.
- **Objetivo de la consulta** — Implementar los casos de uso y una fábrica independiente para memoria y PostgreSQL.
- **Resumen de la sugerencia recibida** — Crear pedidos sin aumentar stock, comprobar permisos, bloquear y revisar idempotencia antes de recibir y coordinar inventario, movimientos, recepción y acumulados en una sola transacción JDBC.
- **Qué revisé, acepté, modifiqué o descarté** — Incorporé los servicios y la fábrica. Acepté que cancelar un pedido parcial cierre el saldo pendiente y conserve lo recibido, y que el reintento con las mismas líneas devuelva la recepción original.
- **Evidencia de verificación** — Diff de `ac86ddd`. Codex ejecutó 15 pruebas focalizadas, incluidas seis del flujo. Informé que la suite de esa etapa aprobó sus 285 pruebas.

### Pantalla JavaFX e historial

- **Fecha** — 2026-11-21.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Cuarto microcommit y ajuste del servicio en `feature/jesus-orders-reception`, `21a5849` y `7a5a2a2`.
- **Objetivo de la consulta** — Preparar la vista independiente de compras con gestión de proveedores, pedidos, recepción e historial, y distinguir confirmación, fallo de refresco y resultado incierto.
- **Resumen de la sugerencia recibida** — Montar la vista mediante `PurchasesController.load(...)`, conservar clave y cantidades para reintentar en la vista abierta y consultar el historial persistido por medio del servicio con `VIEW_INVENTORY`.
- **Qué revisé, acepté, modifiqué o descarté** — Publiqué la vista, el controlador y sus pruebas, y registré por separado el método de consulta en `GoodsReceiptService`. El montaje en la navegación principal quedó para H4.
- **Evidencia de verificación** — Diffs de `21a5849` y `7a5a2a2`. Codex ejecutó 24 pruebas focalizadas con Java 17 y JavaFX, incluidas cuatro nuevas. Una de ellas simula la pérdida de confirmación y comprueba que el reintento no duplica el stock.

### Integración y concurrencia en PostgreSQL

- **Fecha** — 2026-11-22.
- **Herramienta utilizada** — ChatGPT (Codex).
- **Fase, tarea y rama de Git relacionada** — Quinto microcommit de `feature/jesus-orders-reception`, `40838d3`.
- **Objetivo de la consulta** — Añadir pruebas reales de PostgreSQL para el ciclo de compras, el rollback y los reintentos concurrentes, con limpieza limitada a los datos de prueba.
- **Resumen de la sugerencia recibida** — Habilitar la suite solo para `la_exclusiva_test` con V004 instalada. Usar dos conexiones y observar el bloqueo con `pg_blocking_pids(...)`. Limpiar con `DELETE` en orden inverso de claves foráneas.
- **Qué revisé, acepté, modifiqué o descarté** — Publiqué la suite con seis casos. Mantuve la instalación de V004 fuera de las pruebas y la limpieza acotada al proveedor y los productos creados por cada caso.
- **Evidencia de verificación** — Diff de `40838d3`. En la preparación, Codex compiló con Java 17 y comprobó la omisión de los seis casos cuando faltaba la URL de pruebas. Después ejecutamos la suite contra `la_exclusiva_test`. El reporte Surefire de `PurchaseWorkflowPostgresIntegrationTest` registra seis casos ejecutados, cero fallos, cero errores y cero omitidos. Los cinco reportes del módulo suman 24 de 24 pruebas aprobadas. Informé 292 de 293 pruebas aprobadas en la suite global. El único error fue ajeno al módulo y se reportó en `FinancialOperationsPostgresIntegrationTest:208`, por «La fecha del movimiento no es válida».
