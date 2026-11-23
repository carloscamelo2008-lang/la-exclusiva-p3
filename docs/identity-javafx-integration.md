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

## Validación local de la integración

El 23-11-2026 se ejecutó `mvn clean verify` con Microsoft JDK 17.0.18 y Maven
3.10.0. Maven terminó en `BUILD SUCCESS`: **810 pruebas contabilizadas, 0
fallidas, 0 errores, 56 omitidas y 754 ejecutadas con éxito**; también generó el
JAR. Las pruebas de composición, autenticación, revocación, autorización,
controladores JavaFX y carga de FXML se ejecutaron localmente. Esta evidencia es
automatizada; no representa un recorrido manual de todas las pantallas.

No se ejecutaron pruebas contra PostgreSQL en esta validación. Las variables de
conexión de pruebas no estaban disponibles en el proceso Maven y el preflight de
solo lectura de `la_exclusiva_test` encontró V104, V105 y V106 ausentes. Las 56
omisiones corresponden a integraciones PostgreSQL optativas; no se cuentan como
aprobadas. No se aplicaron migraciones ni se modificó ninguna base de datos.
