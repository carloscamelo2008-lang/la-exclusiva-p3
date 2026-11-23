# Integración JavaFX de identidad

## Composición de la aplicación

`ApplicationServices.fromEnvironment()` selecciona `memory` o `postgres` usando
`LAEXCLUSIVA_PERSISTENCE_MODE`. En memoria crea una sola composición
`IdentityServices`: repositorio de usuarios, `AuthenticationService`, validador,
`AuthorizationService` y `UserManagementService`. App y los controladores JavaFX
reciben esas mismas instancias; no hay un segundo repositorio de identidad.

En PostgreSQL, la misma fábrica conserva un `JdbcConnectionProvider` para ventas,
clientes, vendedores, inventario, finanzas y `UserRepositoryJdbc`. El límite de
administración JDBC se construye sobre ese proveedor, por lo que comparte el
contexto de conexión/transacción. Errores de conexión o de esquema se propagan y
el inicio de sesión falla de forma segura; no cambia a memoria.

## Login, sesión y permisos

`LoginController` llama `createSession()` en el emisor de la composición activa.
Credenciales inválidas y errores de persistencia muestran un texto genérico, y el
campo de contraseña se limpia al terminar. Tras autenticar, MainController y las
vistas usan el `AuthorizationService` ligado a `AuthenticationService.sessions()`.
La autorización vuelve a consultar estado, rol, versión e identidad de instancia;
no otorga permisos basándose en `UserSession.getUserAccount()`.

Logout ejecuta `AuthenticationService.revoke(session)` antes de navegar al login.
Si la navegación falla, la sesión sigue revocada y no puede autorizar acciones.
También se revisan los permisos en los handlers y consultas de clientes,
vendedores, inventario, ventas, cartera y caja. La visibilidad de botones no es la
única barrera.

Los métodos comerciales originales se mantienen compatibles con la consola y los
tests históricos, y algunos no reciben una sesión como parámetro. Dentro de
JavaFX, sus operaciones se invocan desde handlers que validan la identidad actual.
`UserManagementService`, a diferencia de esos servicios legacy, exige el actor y
revalida MANAGE_USERS dentro de su frontera administrativa. Una nueva entrada que
llame directamente a un servicio comercial debe añadir una frontera autorizada
con la misma identidad antes de exponerla.

## Administración y alta inicial

La pantalla `user_management_view.fxml` lista resúmenes seguros, sin hashes ni
contraseñas, y permite crear cuentas, cambiar rol, activar/desactivar, restablecer
contraseña y asignar o quitar vendedor. Cada mutación llama a
`UserManagementService`; las restricciones de último administrador, versión,
política de contraseña y exclusividad de vendedor siguen en ese servicio.

En un repositorio vacío se crea el primer administrador con
`LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (por defecto `admin`) y
`LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`. Esta contraseña debe pasar
`PasswordPolicy`; no hay clave fija. La operación se serializa en la frontera y
no reemplaza ni restablece cuentas existentes. El modo `--demo` solo se permite
con persistencia `memory`, requiere `LAEXCLUSIVA_DEMO_ADMIN_PASSWORD` y
`LAEXCLUSIVA_DEMO_WORKER_PASSWORD`, valida ambas antes de escribir y no inserta
cuentas cuando ya hay usuarios.

## Esquema y pruebas PostgreSQL

`UserRepositoryJdbc` requiere V001, V104 y V106; la regla de vendedor activo usa
V105. El arranque no aplica migraciones. La base local `la_exclusiva_test` fue
inspeccionada en modo de solo lectura el 23-11-2026: PostgreSQL 18.6 y tabla
`user_account` presentes, pero V104, V105 y V106 ausentes. No se ejecutaron
migraciones ni escrituras y no se consultó `la_exclusiva`. Por esa razón las
integraciones PostgreSQL de identidad no se habilitaron en esta validación; las
pruebas JDBC simuladas y de composición no se cuentan como pruebas PostgreSQL
reales.

Para habilitar la validación en el laboratorio, aplicar V104, V105 y V106 de
forma controlada únicamente después del preflight aprobado de
`la_exclusiva_test`. La suite opcional de repositorio requiere la propiedad
`-Dlaexclusiva.test.postgres.users=true` y `LAEXCLUSIVA_TEST_DB_URL` dirigida
exactamente a esa base. No utilice la URL normal de desarrollo para esta suite.

## Evidencia de implementación

La prueba JavaFX carga la pantalla de administración mediante FXMLLoader. Las
regresiones cubren composición en memoria/JDBC, login correcto e incorrecto en
memoria, revocación, cambio de rol y desactivación, rechazo a trabajadores en
operaciones administrativas, bootstrap no duplicado, validación de contraseña y
el bloqueo de permisos cuando un snapshot antiguo aún muestra un rol privilegiado.
Los conteos finales de Maven se registran en `docs/ai-usage/leader-ai-log.md`.

## Validación final del PR #24

La validación final se ejecutó en el HEAD publicado de `feature/identity-app-integration`
después de incorporar mediante merge normal `origin/develop` en `9002bf7`.
`mvn clean verify`, con Microsoft JDK 17.0.18 y Maven 3.10.0 en Windows, terminó
en `BUILD SUCCESS`: **814 pruebas contabilizadas, 755 ejecutadas con éxito, 0
fallidas, 0 errores y 59 omitidas**. Se generó el JAR. Las omisiones son
integraciones optativas sin configuración local y no se cuentan como aprobadas.
Las pruebas JavaFX/FXML se ejecutaron automáticamente; no equivalen a un recorrido
manual visual de todas las pantallas.

La validación PostgreSQL real se ejecutó aparte en el laboratorio efímero de
GitHub Actions, asociado al mismo HEAD de la rama. El contenedor usó PostgreSQL
18 (`postgres:18-bookworm`, digest fijado; servidor 18.7), Temurin Java 17.0.20.1
y Maven 3.9.16. Ejecutó **105 pruebas, 0 fallidas, 0 errores y 0 omitidas**:
26 en el ciclo de identidad, 26 en su repetición, 52 en la composición comercial
de `ApplicationServices` y 1 contra un esquema incompleto para comprobar fallo
seguro. El escenario comercial cubrió autenticación persistente, bootstrap sin
duplicados, administración de cuentas, revocación/roles/estado, permisos vigentes,
servicios de clientes, vendedores, inventario, ventas, cartera y caja, replay
idempotente, rollback, persistencia al recrear la composición y rechazo del
fallback a memoria.

El laboratorio crea una base PostgreSQL aislada y temporal, con almacenamiento
efímero y sin puerto publicado. En ese laboratorio se aplicaron los archivos de
migración requeridos por cada escenario (incluyendo V001, V002 y V104/V105/V106;
el escenario comercial también incluye las migraciones de ventas necesarias).
No se ejecutaron migraciones ni escrituras en la instalación local: el preflight
histórico de solo lectura de `la_exclusiva_test` detectó V104/V105/V106 ausentes,
y no se conectó a `la_exclusiva`. La suite especializada también comprobó que la
composición JDBC falla de forma segura ante un esquema incompleto.

Las 6 pruebas de `PurchaseWorkflowPostgresIntegrationTest` no forman parte del
laboratorio especializado (dependen de V004, fuera del alcance de esta integración)
y quedaron entre las pruebas PostgreSQL optativas omitidas por Maven local. No se
presentan como validadas. El primer intento más reciente del job del laboratorio
se detuvo antes de crear la base por una respuesta HTTP 429 de Maven Central; la
repetición del mismo HEAD completó los 105 casos. Los checks finales de GitHub
Actions para el HEAD publicado terminaron correctamente.

Esta evidencia es automatizada. No se afirma haber realizado un recorrido manual
visual completo de JavaFX. La inspección del laboratorio y los conteos detallados
quedan registrados en `docs/ai-usage/leader-ai-log.md`.

## Correcciones de revisión del PR #24

### Frontera de autorización

El constructor sin validador de `AuthorizationService` se conserva para consultas
compatibles sobre sesiones sintéticas, pero `execute` ahora exige una autoridad
de sesión. Por tanto, esa instancia no puede envolver ni iniciar una operación
protegida. Los controladores JavaFX de clientes, vendedores, inventario, ventas,
cartera y caja no crean autorizadores propios: rechazan la configuración hasta
recibir la instancia estricta de `IdentityServices`. Las factorías y la vista de
compras conservan las firmas legacy para compatibilidad de compilación, pero
fallan cerradas si no reciben la autoridad vigente. Las comprobaciones vuelven
a consultar el rol actual al ejecutar la operación.

### Errores de administración

`UserManagementController` solo presenta texto detallado para
`UserManagementService.ValidationException` y mensajes operativos conocidos de
concurrencia o reglas de negocio. Errores JDBC, SQL y estados internos muestran
un mensaje genérico; no se presenta el mensaje de una excepción arbitraria.
La validación de contraseña se tipa sin cambiar su contrato base de excepción,
y las reglas de último administrador y asignación exclusiva conservan sus
excepciones previas. Las otras pantallas usan una lista cerrada de mensajes de
autorización conocidos y ocultan los detalles inesperados de persistencia.

### Cierre de sesión y navegación

Al cerrar sesión, `MainController` revoca la sesión con `AuthenticationService`,
borra la referencia local y deshabilita toda navegación protegida. Muestra de
inmediato el estado «sesión cerrada»; si el callback de navegación falla,
presenta un botón controlado para reintentar el regreso al login. Ese control no
recrea ni renueva sesión, y las operaciones continúan rechazando el token
revocado. Si un callback retorna sin sustituir la escena, la pantalla anterior
permanece visualmente marcada y bloqueada.

Las regresiones de esta revisión se ejecutan en las suites JavaFX/FXML y de
servicios. No utilizan una base PostgreSQL local ni aplican migraciones; la
repetición del laboratorio PostgreSQL efímero y sus resultados se documentan en
la bitácora para el HEAD publicado.

### Validación de las correcciones de revisión (23 de noviembre de 2026)

Con Microsoft JDK 17.0.18 y Maven 3.10.0, `mvn clean verify` terminó en
`BUILD SUCCESS`: **819 casos contabilizados, 0 fallidos, 0 errores y 59
omitidos** (760 ejecutados y aprobados). La suite incluyó las regresiones de
frontera de autorización, mensajes de administración y logout/navegación, junto
con JavaFX/FXML. Las 59 omisiones corresponden a integraciones PostgreSQL
opcionales sin configuración local; no se cuentan como aprobadas. No se ejecutó
ninguna migración ni se accedió a una base local.

El workflow PostgreSQL efímero debe validar el nuevo HEAD publicado porque esta
revisión toca la administración autenticada. Sus resultados se registran cuando
finalice; no se atribuyen a esta ejecución local. Las advertencias de JavaFX sobre
el módulo no nombrado/modelo efectivo siguen sin bloquear Maven. Una ejecución
completa preliminar durante el desarrollo falló por cambios temporales a los
contratos de excepciones del servicio de usuarios; se restauraron dichos
contratos, se mantuvo el filtrado seguro de mensajes y la verificación final
posterior pasó completamente.
