# Asistente de cartera, estados de cuenta y recordatorios

> **Estado de la composición comercial al 28-11-2026:** el envío de correo fue retirado de la ruta de aplicación. `ApplicationServices` rechaza `LAEXCLUSIVA_SMTP_ENABLED=true` y siempre compone un proveedor simulado. Las secciones y resultados históricos que describen vista previa, envío SMTP o CI anterior documentan versiones previas; no son instrucciones para activar correo en la versión actual.

## Alcance y arquitectura

`CollectionAssistantService` ofrece recomendaciones explicables por reglas registradas. No usa un modelo de aprendizaje automático, no predice incumplimiento y no califica moralmente al cliente. La composición se obtiene desde `ApplicationServices.collectionAssistant()`; sus adaptadores en memoria y JDBC usan los puertos `CustomerCollectionRepository` y `AdministrativeAuditRepository`. En PostgreSQL, el seguimiento comparte el `JdbcConnectionProvider` y la frontera transaccional de la aplicación.

La información del perfil se almacena aparte de `Customer`, para no reinterpretar ni reescribir sus campos históricos. V107 crea perfiles de contacto, eventos de seguimiento y avisos; V108 crea eventos administrativos; V109 agrega identidad y evidencia cerrada del intento y el estado de conciliación. Las migraciones son manuales y no se ejecutan al iniciar la aplicación. Revisar [database/README.md](../database/README.md) antes de preparar cualquier entorno.

## Recomendaciones y antigüedad

El tablero usa saldos vigentes, compromisos y movimientos:

1. Compromiso vencido: prioridad alta.
2. Deuda con antigüedad verificable de 30 días o más: prioridad media.
3. Compromiso dentro de los próximos tres días: próximo seguimiento.
4. Otros saldos positivos: prioridad general.

La prioridad de una fila es una categoría única; los contadores del tablero indican por separado compromisos vencidos, cuentas con deuda antigua, compromisos próximos y avisos pendientes de revisión. Las cuentas saldadas se excluyen. Los pagos posteriores a la observación previa del saldo se señalan cuando la fuente histórica permite verificar el cambio.

La antigüedad de una deuda de venta proviene de la fecha de creación de la venta. Para la deuda general se contrasta el saldo con el libro de movimientos. Como el esquema no asigna abonos parciales a cargos individuales ni establece FIFO, el servicio solo informa una fecha cuando el libro permite demostrar que parte del cargo más antiguo sigue pendiente. En casos ambiguos muestra **«Antigüedad no determinada»**; no estima fechas desde el saldo.

El saldo general se mantiene separado de la deuda pendiente de ventas. Una inconsistencia en la que la deuda de ventas exceda el saldo agregado bloquea recomendaciones, documento o aviso para evitar presentar un total financiero incoherente.

## Seguimiento, consentimiento y datos de contacto

Un administrador puede registrar compromiso, nota breve, canal de seguimiento interno, consentimiento para contacto telefónico manual y si el seguimiento semanal está habilitado. Cada cambio genera un evento con actor, fecha y saldo observado. Las notas son para gestión breve; no deben incluir contraseñas, datos bancarios, información sensible innecesaria ni detalles de autenticación.

Los campos y eventos de correo que puedan existir en perfiles históricos se conservan en el esquema para auditoría, pero la interfaz comercial ya no permite editar ni enviar correo. Un perfil histórico de canal `EMAIL` se presenta como no operativo; no se convierte automáticamente en autorización telefónica. La información histórica no se elimina ni se reutiliza como consentimiento telefónico.

Al revocar consentimiento, el servicio fuerza canal `NONE`, desactiva recordatorios, limpia el correo del perfil y deja un evento de revocación. El teléfono existente del cliente no se convierte automáticamente en autorización para contactar. El canal `PHONE` solo genera una tarea de gestión manual: este sistema no hace llamadas ni puede registrar contactos externos que el equipo realice fuera de la aplicación.

La fecha almacenada indica cuándo el operador registró o actualizó la preferencia; **no sustituye la evidencia del consentimiento otorgado por el cliente**. La organización debe conservar esa evidencia por su proceso autorizado. Antes de registrar seguimiento telefónico, el responsable debe confirmar la autorización correspondiente.

La interfaz de administración de contacto requiere `MANAGE_COLLECTION`. El resumen de alertas y la generación del PDF requieren `VIEW_PORTFOLIO`. El servicio y el controlador revalidan permisos; ocultar campos no es el único control. Los detalles de contacto no se entregan en el resumen destinado a trabajadores.

## Estado de cuenta PDF

`statementPdf(UserSession, customerId)` genera bytes bajo una lectura consistente. Incluye cliente, fecha local de generación, ventas pendientes con líneas/precios históricos y pagos registrados, deuda general por separado, saldo agregado y fecha de compromiso si existe. El documento es una comunicación comercial informativa, **no es factura electrónica DIAN ni comprobante fiscal**. El envío no confirma un pago; todo pago debe registrarse y validarse mediante el flujo de caja/abonos existente.

El texto se sanea para la fuente PDF incorporada y se ajusta usando el ancho medido de cada fuente/tamaño y el ancho útil A4; no se usa un límite aproximado de caracteres que pueda cortar nombres largos. Las páginas conservan sus márgenes y el contenido se distribuye en páginas sucesivas. La prueba extrae un nombre ficticio largo y comprueba que se conserva completo y que cada renglón queda dentro del ancho imprimible.

La generación exige `VIEW_PORTFOLIO`. El controlador guarda el archivo en `LAEXCLUSIVA_REPORTS_DIR` o, por omisión, en `%USERPROFILE%\La Exclusiva\Reportes`; usa un nombre único, muestra la ruta y permite abrir la carpeta. El archivo queda fuera de los controles de acceso y auditoría de la aplicación después de exportarse. Debe almacenarse y compartirse como información financiera confidencial; la aplicación no cifra ni administra las copias exportadas. Incluye una presentación azul/blanca y no es factura electrónica DIAN.

La llave de pago se lee de `LAEXCLUSIVA_PAYMENT_KEY`, nunca de una constante. Solo se incorpora al documento cuando también están en `true` `LAEXCLUSIVA_PAYMENT_KEY_OWNER_CONFIRMED` y `LAEXCLUSIVA_PAYMENT_KEY_VERIFIED_BY_ADMIN`. Las confirmaciones y el valor son responsabilidad de administración; la aplicación no verifica titularidad en el banco. No almacenes la llave en Git, bitácoras o pruebas.

## Avisos semanales y correo

El administrador prepara manualmente las alertas internas desde «Preparar seguimiento semanal». No existe un proceso de fondo ni un envío externo. Se prepara como máximo un registro por cliente y semana ISO; la clave única de V107 evita duplicados concurrentes. El lote ayuda a revisar saldos y compromisos, pero no prueba que haya existido una llamada o contacto externo.

El envío comercial de correo está retirado de esta versión. La pantalla no contiene dirección ni acciones de revisión/envío y `ApplicationServices` rechaza `LAEXCLUSIVA_SMTP_ENABLED=true` en ambos modos de persistencia; la composición usa exclusivamente un proveedor simulado. No se deben configurar credenciales SMTP ni habilitar transporte real. Los estados, perfiles y auditorías históricos relacionados con email permanecen consultables como historial y no reactivan una ruta de envío.

Los servicios y tipos de proveedor históricos todavía contienen contratos de revisión SMTP por compatibilidad y pruebas, pero no se componen en la aplicación. La ruta productiva no crea tokens de envío ni llama a SMTP. Si se retomara esa funcionalidad, requiere decisión operativa, nueva revisión de seguridad y validación humana; no basta con añadir variables de entorno.

En versiones anteriores, el claim PostgreSQL bloqueaba primero la fila del cliente y luego el perfil antes de pasar a `SENDING`. Esa implementación e integración se conserva en las secciones históricas de este documento; no existe una acción de envío comercial en la interfaz ni una composición SMTP activa en la versión actual.

No hay variables SMTP activas en esta composición. Las pruebas usan proveedores simulados o dobles; no envían mensajes reales. El esquema V109 conserva evidencia histórica de intentos (`SMTP_ACCEPTED`, `SMTP_REJECTED`, `SMTP_OUTCOME_UNCERTAIN` o `SIMULATED`) sin almacenar dirección, token, credenciales, cuerpo ni PDF en auditoría.

Los estados `SENDING` y `RECONCILIATION_REQUIRED` que ya existan representan intentos históricos. Se conservan para que administración pueda auditarlos; no se reintentan ni se generan nuevos intentos de correo desde la aplicación actual.

Al preparar otra vez la semana, el mismo registro solo puede actualizarse si todavía tiene cero intentos y está pendiente de revisión o marcado por saldo cambiado. El saldo nuevo queda en la fila semanal existente y se registra `BALANCE_CHANGED`; cualquier vista previa anterior se invalida. Filas en `SENDING`, `SENT`, con rechazo del proveedor o en conciliación nunca se reactivan automáticamente. La unicidad por cliente/semana permanece impuesta por PostgreSQL.

La política horaria que se describe a continuación pertenecía al envío SMTP de versiones anteriores y no habilita envíos en la composición actual. No se requiere configurar estas variables para las alertas internas:

| Variable | Descripción |
|---|---|
| `LAEXCLUSIVA_COLOMBIA_HOLIDAYS_YEAR` | Año para el calendario configurado; debe coincidir con el año actual |
| `LAEXCLUSIVA_COLOMBIA_HOLIDAYS` | Fechas festivas del año, separadas por coma (`AAAA-MM-DD`) |

Los intentos históricos pueden haber usado un calendario `America/Bogota` de lunes a viernes 07:00–19:00 y sábado 08:00–15:00. Los valores siguientes se conservan solo para interpretar esa configuración histórica; la composición actual no los lee para efectuar envíos.

Los límites de revocación concurrente descritos en versiones anteriores quedan como requisito de seguridad si se vuelve a proponer SMTP. En la ruta vigente no hay transporte externo. La revocación de sesión bloquea operaciones posteriores; los datos ya cargados pueden permanecer visibles hasta refrescar o salir de la vista.

Referencias normativas primarias: [Ley 2300 de 2023, Función Pública](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=213990) y [Ley 1581 de 2012, texto oficial](https://www.suin-juriscol.gov.co/archivo/listado/habeasdataSUIN.pdf). La organización debe obtener y conservar evidencia válida del consentimiento y atender revocaciones o solicitudes recibidas por canales externos.

## Auditoría y permisos

`AdministrativeAuditService.list(session, desde, hasta, límite)` exige `VIEW_ADMIN_AUDIT`, y la autoridad de sesión valida cuenta activa, rol y sesión vigente. Se registran usuario creado, cambios de rol, activación/desactivación, restablecimiento de contraseña, asignación de vendedor y resultado de intentos de correo de estado de cuenta. Cada fila contiene actor, acción cerrada, tipo/identificador de objetivo, hora y resultado. No guarda hashes, contraseñas, tokens, detalles SQL, cuerpo de correo ni adjuntos.

Para que el dashboard de Daniel pueda consumir la funcionalidad sin acoplarse a controladores:

```java
CollectionDashboard board = services.collectionAssistant().dashboard(session);
board.overdueCommitments();
board.agedDebts();
board.upcomingCommitments();
board.statementsPendingReview();
board.recommendations();
```

`dashboard(session)` vuelve a validar `VIEW_PORTFOLIO`. Los cuatro contadores y las filas deben ocultarse/limpiarse si esa autorización falla; no copiar el detalle de contacto al dashboard. La gestión de perfiles, historial y preparación de seguimiento interno requiere `MANAGE_COLLECTION`; auditoría requiere `VIEW_ADMIN_AUDIT`. La aplicación actual no expone revisión ni envío de correo. No se modifica la rama visual de Daniel con este contrato.

## Pedidos a domicilio: pendiente independiente

La entidad `purchase_order` representa pedidos a proveedores y no se reutiliza para entregas a clientes. No hay un agregado de pedidos domiciliarios en este cambio. El contrato mínimo futuro debería incluir pedido y líneas con cantidades/precios acordados, estados y transiciones permitidas (incluido «en camino»), actor/fecha/auditoría, idempotencia, consentimiento y canal autorizado. Solo entonces se podría preparar un aviso ligado a la transición. No existe ni se propone GPS o consulta de ubicación del repartidor.

## Verificación y límites

Las pruebas unitarias comprueban reglas, permisos, consentimiento, PDF, simulación, idempotencia y casos de error. `CustomerCollectionPostgresIntegrationTest` es optativa; en CI se ejecuta solo en el laboratorio efímero autorizado.

Validación del HEAD `a8cbf6b87eb2daf97825180bfffbf89ebe2ed944` (23-11-2026): la acción [`identity-postgres` 45276566584](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45276566584) terminó correctamente con **112/112** pruebas PostgreSQL, cero fallos, errores u omisiones. Incluyó identidad 26/26 en dos ciclos, aplicación 59/59 (incluida `CustomerCollectionPostgresIntegrationTest`, 1/1) y esquema incompleto 1/1. El laboratorio efímero usó PostgreSQL 18 y Java 17 desde imágenes fijadas por digest; aplicó la ruta V001, V004, V002 y V100–V108, verificó las migraciones y destruyó el clúster al terminar. El registro sanitizado marcó `completed=true` y `destroyed=true`.

La primera ejecución del mismo HEAD antes de la corrección de fixture falló al limpiar un usuario: su evento de asignación de vendedor aún referenciaba `administrative_audit_event` mediante FK `RESTRICT`. La limpieza se ajustó para borrar únicamente eventos cuyo actor/objetivo/cliente corresponde a ese fixture, dentro de la transacción acotada. La ejecución final en `a8cbf6b` pasó y el escenario posterior de bootstrap confirmó la base vacía. La verificación local omitió integraciones PostgreSQL por falta deliberada de variables de conexión; esa omisión no se cuenta como resultado PostgreSQL.

No ejecutar estas migraciones en bases locales existentes como parte de esta validación. Las pruebas no enviaron correos reales; la integración SMTP real continúa sin validación operativa.

Revisión final del PR #27 en el commit `9e35255c36c69745f84e62b9996b38c6cca852c4` (23-11-2026): el email ahora se persiste solo para el canal `EMAIL` con consentimiento, tanto en el modelo como en V107; al cambiar el destino se registra un cambio de autorización y el controlador refleja el perfil guardado. El PDF divide líneas por la medida de la fuente y el ancho útil A4. Se añadieron regresiones del servicio, FXML y restricción PostgreSQL. `mvn clean verify` con Java 17/Maven 3.10.0 aprobó **776 pruebas**, con 0 fallos y 0 errores; 61 integraciones optativas se omitieron porque no se configuraron variables de PostgreSQL. Las pruebas dirigidas de cartera/FXML aprobaron 12/12. En GitHub Actions, [`identity-postgres` 45283339551](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45283339551) aprobó **112/112** sobre el mismo commit: identidad 26/26 en dos ciclos, aplicación 59/59 (incluida `CustomerCollectionPostgresIntegrationTest`, 1/1) y esquema incompleto 1/1. El artefacto sanitizado confirmó `source_head` coincidente, migraciones V001/V002/V004/V100–V108, `completed=true` y `destroyed=true`; usó PostgreSQL 18 y Java 17 fijados por digest. No se conectó a una base local ni se envió correo real.

En la revisión se comprobó que `CollectionAssistantService` exige permisos vigentes para cartera, perfil, avisos y envíos, y que `AdministrativeAuditService` exige `VIEW_ADMIN_AUDIT`; `CollectionsController` no es la única frontera. La composición predeterminada usa entrega simulada, y la configuración SMTP solo se construye con habilitación explícita. Pendientes antes de activar SMTP: conservar evidencia válida de consentimiento y verificar el nuevo destinatario, completar y revisar el calendario anual, definir la respuesta a una revocación concurrente y operar de manera segura con PDF exportados. La revocación de sesión bloquea operaciones posteriores, aunque la pantalla abierta no se borra automáticamente hasta refrescar o salir.

## Validación final del laboratorio PostgreSQL — PR #27

Sobre el HEAD `ca6ddf9f84fef7392fc5b3b11709f92871062d03`, [el laboratorio efímero de GitHub Actions, run 45302806390](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45302806390), terminó correctamente con **113/113 pruebas PostgreSQL aprobadas**, sin fallos, errores ni omisiones: identidad 26/26 en el ciclo inicial y 26/26 en la repetición; aplicación 60/60; esquema incompleto 1/1. El runner exige estos totales exactos antes de marcar la ejecución completa.

La ruta aplicó y validó V109, incluyendo la conversión de registros históricos ambiguos a `RECONCILIATION_REQUIRED`. El escenario de aplicación incluyó las regresiones de conciliación SMTP persistente y recuperación tras reinicio. También pasó la limpieza corregida del fixture con actor compartido. El job de laboratorio, el paso de limpieza externa y la publicación del artefacto sanitizado terminaron correctamente; el laboratorio temporal se destruyó. Se usaron PostgreSQL 18 y Java 17 con imágenes fijadas por digest. No se conectó a bases locales y SMTP real permaneció desactivado.

La descarga de `maven-resources-plugin:3.4.0` había recibido HTTP 429 en dos ejecuciones anteriores, antes de iniciar PostgreSQL. Para reducir descargas repetidas, el workflow restaura la caché Maven compartida con el build, copia únicamente el repositorio de artefactos a un directorio job-local validado y ejecuta los ciclos con el seed de solo lectura y Maven offline. La ejecución final pasó; no se agregaron reintentos ni se omitieron pruebas.
