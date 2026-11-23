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

IdentityServices comparte su authentication() y sessions(). No conecta App,
ApplicationServices ni controladores comerciales. Quien integre no debe crear
otro emisor para validar sesiones del primero. Tampoco debe duplicar repositorios
o proveedores JDBC. El constructor público UserSession se conserva, pero por sí
solo no acredita autenticación. AuthorizationService conserva su API anterior:
los consumidores que solo lo usan sobre snapshots no quedan protegidos por 4D.

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

## Integración pendiente

Carlos debe conectar el emisor, validador y principal en ApplicationServices y
operaciones comerciales, con sus permisos y transacciones. Jesús debe revisar
seguridad y coordinar compras/recepción. No se modificaron esos módulos ni se
conectó logout a navegación. Los valores de rol/vendedor cacheados por vistas
antiguas no se actualizan mágicamente; deben utilizar el resultado vigente o exigir
nuevo login. Las pruebas sintéticas de administración se adaptaron a emisión con
credenciales reales de fixture; no se añadió un bypass de producción para tests.

## Evidencia y límites

Las ejecuciones iniciales y finales se registran en la bitácora. Las integraciones
PostgreSQL permanecen omitidas, sin URL de pruebas y sin aplicar migraciones. La
prueba concurrente controla con latches una lectura en curso y una revocación;
otra comprueba repetidamente el rechazo después de completarse la revocación.
La eliminación por GC se apoya en las garantías de claves débiles del contenedor,
no en un test dependiente de forzar System.gc(). Persistencia real, integración
comercial y revisión independiente siguen pendientes.
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
