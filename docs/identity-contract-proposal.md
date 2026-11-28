# Propuesta de contratos de identidad y administración

**Estado: PROPUESTA PENDIENTE DE APROBACIÓN de Carlos y Jesús.**

Fecha: 18/11/2026. Responsable: Daniel, Desarrollador 2.
Rama: `feature/daniel-identity-users`. Base: `origin/develop`,
`46b061f25f302e9a242c5244d5455d3e367d7b50`.
Referencia: secciones 2, 3 y 6–10 de `Plan_Trabajo_Equipo_La_Exclusiva.pdf`.
Esta ficha propone contratos; no autoriza implementación, migraciones ni cambios compartidos.

## Alcance y contratos propuestos

| Área | Propuesta para acordar |
| --- | --- |
| Repositorio | Crear `repository.jdbc.UserRepositoryJdbc` sobre `user_account`, compatible con `UserRepository`: conservar `save`, búsquedas por ID/username, listado, `update` y `delete`, incluidos los parámetros `String` para IDs. Recuperar el ID generado y conservar los datos históricos al cargar. No ofrecer borrado físico desde la pantalla. |
| Persistencia | Reutilizar `JdbcConnectionProvider`, `JdbcTransactionManager` y SQL parametrizado. Respetar el índice único `lower(btrim(username))`, la FK `seller_id`, los roles y el estado activo de V001. No duplicar tablas ni repositorios de otros módulos. |
| Autenticación | Mantener `AuthenticationService` y el formato PBKDF2 de `PasswordHasher`. PostgreSQL es la persistencia comercial predeterminada; `memory` queda para pruebas automatizadas y no crea usuarios ficticios. El administrador inicial requiere configuración explícita y no se recrea si ya existen cuentas. No sustituir una conexión fallida por memoria silenciosamente. |
| Bootstrap | Proponer `UserBootstrapService`: creación inicial controlada solo cuando corresponda, sin restablecer contraseñas ni recrear cuentas al reiniciar. Serializar dos bootstrap simultáneos mediante un mecanismo transaccional compartido por todos los arranques. Definir el bloqueo con el equipo. |
| Administración | Proponer `UserManagementService` para alta, consulta, cambio de rol, activación/desactivación, vendedor asignado y restablecimiento de contraseña. Exigir sesión vigente y `MANAGE_USERS` dentro del servicio. No devolver hashes a la vista ni registrar contraseñas. |
| Último administrador | Comprobar y serializar las desactivaciones y cambios de rol que reduzcan administradores activos; impedir que quede cero, incluida la autodesactivación del único administrador. La garantía debe cubrir operaciones concurrentes. |
| Sesión y vendedor | Consultar vigencia persistida antes de admitir nuevas acciones protegidas. Invalidar y exigir nueva autenticación si cambian rol, vendedor, estado o credenciales de seguridad; impedir uso de asignaciones antiguas. Denegar la acción si no puede comprobarse la vigencia. Acordar una extensión acotada de `UserSession`/autenticación y si requiere revisión de seguridad persistente. Las operaciones ya iniciadas necesitan una política explícita; no se promete cancelarlas. |
| JavaFX | Crear `UserManagementController` y `user_management_view.fxml`, con listado, formulario, confirmaciones y mensajes en español. Mostrar solo a administradores y revalidar permisos al ejecutar. Separar escritura confirmada, refresco fallido y resultado incierto; no reintentar escrituras automáticamente. |
| Clientes | Proponer una sobrecarga `CustomerService.registerCustomer(name, phone, address)` que devuelva el cliente con ID generado. Conservar la firma actual de cuatro argumentos. Usar UUID dentro de `VARCHAR(64)`, unicidad de base de datos y tratamiento explícito de colisiones; evitar `MAX(id)+1`. Adaptar únicamente el formulario de clientes tras acordar la firma. |

## Esquema y puntos de integración

- No modificar V001/V002. Proponer V003 solo si se justifica y aprueba un cambio de esquema para identidad, por ejemplo una revisión de seguridad de cuenta. No aplicar migraciones desde la aplicación ni desde pruebas sin autorización.
- Entregar una fábrica de identidad y contratos de configuración probados, sin cambiar `ApplicationServices`, `MainController`, `main_menu_view.fxml`, `pom.xml`, README raíz ni tablero global. Carlos conectará la composición y la navegación central en su integración.
- `App.java` pertenece al alcance de Daniel para componer autenticación una sola vez y reutilizarla durante login/logout; su cambio se implementará después del acuerdo. La fábrica no creará servicios comerciales duplicados.
- Coordinar con Carlos el efecto de las sesiones revocadas sobre ventas y pagos y la nueva firma de clientes. Reutilizar vendedores y sus IDs sin modificar finanzas, pedidos, recepción o inventario de Carlos y Jesús.

## Pruebas y aceptación previstas

- Unitarias: credenciales, normalización y duplicados, roles, permisos, cuentas inactivas, vendedor inexistente, último administrador e IDs de clientes.
- JavaFX: formularios sin hashes, validaciones y confirmaciones, acceso denegado, logout/cambio de usuario, sesión invalidada y escritura confirmada seguida de refresco fallido.
- PostgreSQL: reinicio real, conservación de usuarios y asignaciones, claves generadas/FK, duplicados concurrentes, dos bootstrap simultáneos, protección concurrente del último administrador y rollback.
- Integración: trabajador asignado registra una venta propia y no ajena; cambios administrativos bloquean nuevas escrituras de la sesión anterior. Conservar modo memoria y regresiones existentes.
- Ejecutar Maven con Java 17 y distinguir pruebas aprobadas de omitidas. PostgreSQL únicamente en `la_exclusiva_test`, con autorización previa, fixtures propios y limpieza acotada. JavaFX 17.0.2 y dependencias actuales; sin nuevas dependencias previstas.

Antes de programar, Carlos y Jesús deben aprobar o ajustar las firmas, política de sesiones,
bloqueos de bootstrap/último administrador, posible V003 y puntos de montaje. La aprobación no
está registrada todavía. Código y commits en inglés; JavaDoc, documentación y mensajes en español.

## Coordinación confirmada de numeración — 22/11/2026

Carlos confirmó conservar V104 para versiones de seguridad y reservar V105 para
exclusividad de vendedor activo, como migraciones independientes. La posible V003
mencionada en la propuesta inicial queda sustituida por esta numeración. V004 de
Compras y Recepción ya está integrada en develop; las migraciones financieras
ocupan V100–V103. Esto no constituye aprobación íntegra de los demás contratos
pendientes. La revisión final de seguridad y SQL corresponde a Carlos y Jesús.
## Estado de la entrega independiente — 22/11/2026

La cabecera y las propuestas iniciales se conservan como contexto histórico. Carlos
ha autorizado los desarrollos de repositorio JDBC, composición independiente,
versionado V104, exclusividad V105, administración protegida y sesiones emitidas,
política de contraseñas y endurecimiento PBKDF2. También autorizó su publicación y
un PR hacia develop. La revisión independiente de Jesús sobre el conjunto no se
considera completada por esta autorización.

La entrega es una base de identidad revisable, no la integración completa en la
aplicación. AuthenticationService y su validador deben proceder de la misma
IdentityServices; los constructores administrativos sin emisor fallan cerrado.
No se añadieron usuarios predeterminados ni fallback JDBC a memoria. No se afirma
que App ya autentique usuarios PostgreSQL. Mantiene su aprovisionamiento en memoria.

Quedan para acuerdos e integración posteriores: bootstrap persistente concurrente
que no restablezca contraseñas; montaje en ApplicationServices y navegación por
Carlos; pantalla de gestión de usuarios; UUID automático de clientes sin cambiar
contratos compartidos; revalidación en operaciones comerciales y uso del vendedor
actual por los responsables. No se implementaron estos cambios al cerrar 4D ni se
decidieron firmas nuevas por el equipo. No se modificaron módulos de Carlos/Jesús.

V104 y V105 están preparadas, sin aplicar. Los 40 casos PostgreSQL omitidos de la
suite actual son pendientes, no aprobaciones. La medición PBKDF2 real y sus límites
están en password-verification.md; no existe inventario completo de hashes reales.
Antes de despliegue se requieren revisión de seguridad, autorización explícita de
migraciones y pruebas aisladas. No se aprueba ni fusiona el PR desde esta entrega.
