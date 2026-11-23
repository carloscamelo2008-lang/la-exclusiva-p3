# Sesiones autenticadas y vigentes — Actividad 20, 4D

## Confianza y emisión

Cada AuthenticationService posee una autoridad privada, sin registro global. Solo
createSession registra una sesión después de comprobar credenciales mediante su
rutina privada. Sobrescribir authenticate no permite emitir sin verificar. No hay
una operación pública para registrar sesiones arbitrarias. El repositorio de esa
instancia es el único utilizado al autenticar y revalidar.

Se registran identidad y versión inmutables, separadas del UserAccount mutable.
Solo se aceptan objetos exactos UserSession emitidos por esa autoridad; una sesión
manual, una subclase o la sesión de otro emisor se rechazan aunque coincidan sus
datos públicos. Esto es una frontera entre componentes confiables del proceso,
no un sandbox contra ejecución arbitraria de Java, reflexión o acceso al repositorio.
No se agregan tokens de red ni persistencia/serialización de sesiones.

## Contratos

AuthenticationService.sessions() devuelve un validador estable. Su validate(session)
comprueba emisión, cuenta persistida existente/activa, identidad y securityVersion.
Devuelve AuthenticatedPrincipal(userId, role, sellerId, securityVersion), un record
sin hash, contraseña, cuenta mutable o permisos administrativos implícitos. El
principal es un resultado de datos, no una credencial transferible que sustituya
validar una sesión. Los errores de repositorio se propagan sin fallback o reintento.

El validador general no exige MANAGE_USERS. AdministrativeSessionValidator lo
reutiliza y exige además ese permiso a partir del rol vigente. Los constructores
anteriores de administración se conservan pero fallan cerrado por falta de emisor;
no admiten sesiones sintéticas. La sobrecarga nueva recibe AuthenticationService
y exige exactamente el mismo objeto UserRepository. UserManagementService incorpora
la sobrecarga (users, boundary, authorization, sellers, authentication).

IdentityServices conserva una sola instancia de authentication() y sessions().
La integración JavaFX de `feature/identity-app-integration` la compone en
ApplicationServices y la comparte con App y controladores. En memoria, emisor y
servicio administrativo usan el mismo InMemoryUserRepository; en PostgreSQL,
UserRepositoryJdbc y JdbcUserAdministrationBoundary usan el JdbcConnectionProvider
de la composición comercial. El constructor público UserSession se conserva, pero
por sí solo no acredita autenticación. La sobrecarga productiva de
AuthorizationService recibe el validador de esa misma autoridad y decide con el
rol actual; el constructor sin validador permanece solo para consumidores legacy
y pruebas sintéticas, no se inyecta en el flujo JavaFX.

## Revocación, concurrencia y ciclo de vida

revoke(session) retira solo una sesión propia e invalida su indicador volatile.
Es idempotente y no modifica usuarios ni otras sesiones. invalidate() también
sigue siendo idempotente y su visibilidad entre hilos queda garantizada por volatile.
Después de completar una revocación, las nuevas validaciones se rechazan.

El registro WeakHashMap privado utiliza claves débiles; los valores solo contienen
ID y versión, sin referencia de vuelta a la sesión. Las sesiones emitidas son de
la clase exacta UserSession, que conserva equals/hashCode de identidad de Object.
Todos los accesos al registro se sincronizan en el mismo monitor. No se retiene
fuertemente una sesión abandonada. Las entradas recolectadas se purgan durante
accesos del mapa; cleanSessions() también retira sesiones invalidadas y nunca las
registra de nuevo. Sin actividad no crece el registro; no se exige un hilo global
ni temporizador. La autoridad completa termina con su composición.

No se mantiene el monitor del registro durante consultas JDBC/repositorio, evitando
invertirlo con el monitor administrativo. Se comprueba de nuevo emisión y validez
antes de devolver el principal. Una revocación durante la lectura impide finalizar
esa validación. Una revocación posterior al punto final de comprobación puede
coincidir con el retorno: no se cancelan acciones ya autorizadas o iniciadas.

El estado persistido se verifica al leerlo, no queda bloqueado por este validador.
Las operaciones protegidas deben validar y exigir su permiso dentro de la frontera
transaccional correspondiente. Una revocación/cambio posterior a la lectura no
revierte una operación en curso. Los escritores que omitan versionado o bloqueos
siguen fuera del protocolo; V104/V105 no se aplicaron en esta actividad.

## Integración JavaFX

`App` obtiene el repositorio de usuarios y AuthenticationService de
ApplicationServices; ya no construye un repositorio paralelo en memoria cuando el
modo seleccionado es PostgreSQL. Login usa ese emisor, presenta un error genérico
para fallos de autenticación/persistencia y limpia el campo de contraseña. Logout
llama a `AuthenticationService.revoke()` antes de volver al login, incluso si la
navegación posterior falla.

MainController entrega el mismo AuthorizationService a Clientes, Vendedores,
Inventario, Ventas, Cartera y Caja. Los handlers y consultas vuelven a comprobar
los permisos antes de acceder a datos o ejecutar escrituras; ocultar botones solo
mejora la navegación y no es el control de acceso. La pantalla de administración
consulta resúmenes sin hashes y delega creación, rol, estado, restablecimiento de
contraseña y asignación de vendedor en UserManagementService, que vuelve a
validar MANAGE_USERS dentro de UserAdministrationBoundary.

Los servicios comerciales existentes conservan sus contratos compatibles con la
consola y pruebas antiguas; varios no reciben UserSession directamente. Por ello
la frontera autorizada de esos flujos es la operación JavaFX, cuyos handlers usan
el validador actual, mientras UserManagementService protege internamente sus
operaciones. Si se expone un caso de uso comercial desde otra entrada nueva, esa
entrada debe repetir la autorización con la misma autoridad o evolucionar el
contrato para recibir un contexto de operación; no debe confiar en la visibilidad
de controles.

El administrador inicial se crea solo si el repositorio está vacío, con
`LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (por defecto `admin`) y
`LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`, validada por PasswordPolicy. Las cuentas
existentes nunca se reescriben. `--demo` se limita a `memory`, requiere las dos
contraseñas seguras y solo inserta ambas cuentas cuando el repositorio está vacío.
Con PostgreSQL, falta de conexión, tabla o columnas V104/V105/V106 detiene el
arranque: no existe fallback a usuarios en memoria. No se aplican migraciones al
arrancar.

## Evidencia histórica de la incorporación del módulo

Las ejecuciones iniciales y finales se registran en la bitácora. Las integraciones
PostgreSQL permanecen omitidas, sin URL de pruebas y sin aplicar migraciones. La
prueba concurrente controla con latches una lectura en curso y una revocación;
otra comprueba repetidamente el rechazo después de completarse la revocación.
La eliminación por GC se apoya en las garantías de claves débiles del contenedor,
no en un test dependiente de forzar System.gc(). Los conteos siguientes pertenecen
a la fase de identidad previa a su conexión con JavaFX; para la evidencia actual
véase la sección de integración agregada al final de este documento.
Resultado final observado: mvn -o verify con Temurin 17.0.21+9 y release 17,
748 casos contabilizados, 708 aprobados, 0 fallos, 0 errores y 40 omitidos de
integración PostgreSQL. Incluye 16 casos del validador general, 18 administrativos,
118 de gestión y las regresiones existentes. La ejecución inicial de test contó
732 casos (692 aprobados y 40 omitidos); se repitió verify tras añadir cobertura y
endurecer emisión frente a sobrescritura de authenticate. No se instalaron
herramientas ni dependencias. Las variables de URL se retiraron solo del proceso
de pruebas para impedir conexiones reales; no se cambió configuración permanente.
## Auditoría y refuerzo para revisión externa

La auditoría posterior no requirió cambios de producción. Se añadieron pruebas
para rechazar una subclase de UserSession con equals/hashCode y accesores hostiles
antes de invocarlos; sobrescribir createSession no registra una sesión arbitraria;
revocar desde otra autoridad no afecta la sesión propia; y tanto revoke como
invalidate durante una lectura impiden terminar la validación. El punto de
comprobación final sigue siendo el límite de operaciones concurrentes ya explicado.

La ejecución posterior mvn -o verify con Temurin 17.0.21+9 contabilizó 751 casos:
711 aprobados, cero fallos/errores y 40 integraciones PostgreSQL omitidas. Incluye
19 casos del validador general, los 18 administrativos y 118 de gestión. No se
interpretan las omisiones como aprobación de PostgreSQL. La revisión independiente
de Carlos y Jesús y la integración comercial permanecen pendientes.

## Corrección posterior del PR #22: identidad de instancia

Además del ID y security_version, emisión y validación capturan y comparan el
UUID persistente de cada inserción. Una sesión anterior no valida una cuenta
recreada con el mismo ID y versión, incluso si conserva contraseña o cambia rol.
AuthenticatedPrincipal incluye el UUID actual como quinto componente inmutable.
Las garantías de autoridad, revocación y operaciones ya iniciadas no cambian.
Véase [identidad de instancia](account-instance-identity.md) para el contrato de
snapshots, migración V106 y validaciones PostgreSQL todavía pendientes.

## Validación de la integración JavaFX del PR #24

En el HEAD final publicado se ejecutó `mvn clean verify` local con Microsoft JDK
17.0.18 y Maven 3.10.0: 814 casos contabilizados, 755 aprobados, 0 fallidos, 0
errores y 59 omisiones de pruebas optativas PostgreSQL. Por separado, el laboratorio
efímero de GitHub Actions ejecutó 105 pruebas reales contra PostgreSQL 18.7 con
Temurin 17.0.20.1 y Maven 3.9.16: 0 fallos, errores u omisiones. Incluyó dos ciclos
de 26 pruebas de identidad, 52 pruebas de composición de aplicación/servicios y
una prueba de esquema incompleto que verifica el fallo seguro. Solo el laboratorio
efímero recibió migraciones; la base local no fue modificada. La cobertura y el
límite de las seis pruebas `PurchaseWorkflowPostgresIntegrationTest` omitidas se
detallan en `docs/identity-javafx-integration.md` y en la bitácora de IA.

El constructor de compatibilidad de `AuthorizationService` permite consultar
permisos de un snapshot, pero nunca ejecutar una operación mediante
`execute`: esa ruta exige el validador conectado al emisor de sesiones. Los
controladores JavaFX y el módulo de compras requieren dicha autoridad durante la
configuración. Tras logout, la pantalla queda cerrada y bloqueada aunque falle
la navegación; el botón de recuperación solo vuelve a intentar abrir login y no
restaura la sesión revocada.

## Correcciones de revisión del PR #24 (validación local)

El 23 de noviembre de 2026, `mvn clean verify` con Microsoft JDK 17.0.18 y Maven
3.10.0 terminó en `BUILD SUCCESS`: 819 casos contabilizados, 0 fallidos, 0 errores
y 59 pruebas PostgreSQL opcionales omitidas (760 casos ejecutados y aprobados).
La ejecución incluyó las regresiones JavaFX/FXML de autorización, mensajes
seguros de administración y recuperación de logout. No se usó PostgreSQL local ni
se aplicaron migraciones. La repetición en el laboratorio PostgreSQL efímero de
CI queda pendiente para este HEAD y se documentará cuando haya terminado.
