# Plan de validación PostgreSQL de identidad — pendiente de autorización

Esta entrega prepara archivos y pruebas, **no ejecuta SQL, Docker ni migraciones**.
No inspecciona una base real: el esquema conocido procede de los archivos del
repositorio. Carlos confirmó resueltos los dos bloqueantes del PR #22; la revisión
de Jesús y la autorización específica de ejecución siguen siendo necesarias.

## Entorno desechable propuesto

`database/validation/identity/compose.yaml` define un proyecto independiente:

- PostgreSQL 18 por imagen/digest aprobados, ya disponible localmente; pull_policy
  never evita descargas implícitas. No usar latest ni asumir un digest inventado.
- Datos en tmpfs de `/var/lib/postgresql`, sin volumen persistente, sin datos del
  host y sin restaurar copias de bases reales. Es la ubicación de volumen de la
  imagen oficial desde PostgreSQL 18; [referencia oficial](https://github.com/docker-library/docs/blob/master/postgres/README.md).
- Red interna sin puertos publicados y PostgreSQL escuchando solo en loopback.
  El contenedor Java comparte exactamente su espacio de red. Así las pruebas ven
  127.0.0.1:5432 y satisfacen las comprobaciones existentes de inet_server_addr();
  no apuntan al PostgreSQL instalado en Windows ni a servicios externos.
- Repositorio montado solo lectura. Java copia únicamente pom.xml, src, database
  y docs a tmpfs; no copia .git, settings.xml ni credenciales del host. Repositorio
  Maven Linux/Java 17 completo, previamente preparado, copiado a otra tmpfs para
  ejecutar offline. Imágenes y caché también requieren revisión de procedencia.
- Secreto aleatorio exclusivo del laboratorio, en archivo temporal externo al
  repositorio y con acceso restringido al usuario. Se monta mediante Docker
  secrets; no incluir su contenido en comandos, capturas, logs o evidencias.
- Usuario `identity_validation_owner`, base exacta `la_exclusiva_test`,
  cluster_name `laexclusiva-identity-validation`. Las barreras SQL y Java comprueban
  ese marcador además del nombre. Es aislamiento contra destinos accidentales,
  no prueba criptográfica contra un administrador malicioso del equipo Docker.

Docker no se encontró en PATH durante la preparación; no se instaló ni inició.
Java local disponible: Temurin 17.0.21+9. No se ha validado Compose, imágenes,
montajes, caché Linux ni runtime del laboratorio. Hace falta un equipo Docker
autorizado o una VM desechable equivalente. No sustituirlo por la base de pruebas
compartida existente. No se cambia el puerto ni se detienen servicios Windows.

## Dependencias y aplicación

Para identidad basta V001 y después **V104 → V105 → V106**. V001 define seller y
user_account, FK, identidad numérica y username normalizado. V104 agrega versión,
CHECK y trigger; V105 exige que no haya vendedores activos duplicados; V106 agrega
UUID y hace backfill. Al cambiar solo UUID, V104 conserva security_version.
V002 y V100–V103 no son dependencias de estas 19 integraciones de identidad y no
se aplican en este laboratorio mínimo. No se cambian migraciones existentes.
Una validación posterior del despliegue completo debe coordinar las migraciones
financieras y de Compras con sus responsables, separada de este alcance.

Los archivos usan BEGIN/COMMIT y no son idempotentes. No envolver las inclusiones
en otra transacción: PostgreSQL no ofrece transacciones anidadas con BEGIN.
Un fallo revierte la migración en curso al cerrar la conexión; las migraciones
anteriores ya confirmadas permanecen. El proyecto usa scripts SQL, no una tabla
automática de historial Flyway. Registrar SHA-256 de cada archivo, commit, orden,
salida y estado final de catálogos; no inferir aplicación por un nombre de versión.

## Comandos propuestos: NO ejecutar antes de autorizar

Desde la raíz del checkout, definir únicamente rutas y referencias revisadas:

```powershell
$env:LAEXCLUSIVA_PG18_IMAGE = 'postgres:18@sha256:<digest aprobado>'
$env:LAEXCLUSIVA_JAVA17_IMAGE = 'maven:<tag Temurin 17 aprobado>@sha256:<digest aprobado>'
$env:LAEXCLUSIVA_LAB_SECRET_FILE = '<ruta absoluta al secreto temporal exclusivo>'
$env:LAEXCLUSIVA_MAVEN_REPOSITORY = '<ruta absoluta a cache Linux Maven offline completa>'
$lab = 'database/validation/identity/compose.yaml'
docker compose -f $lab config --quiet
docker compose -f $lab up -d --wait postgres
docker compose -f $lab exec -T postgres psql -X -w -U identity_validation_owner -d la_exclusiva_test -f /workspace/database/validation/identity/validate.psql
if ($LASTEXITCODE -ne 0) { throw 'Detener validación y conservar evidencia sin secretos' }
docker compose -f $lab exec -T postgres psql -X -w -U identity_validation_owner -d la_exclusiva_test -f /workspace/database/validation/identity/inspect.psql
if ($LASTEXITCODE -ne 0) { throw 'Falló captura de metadatos' }
docker compose -f $lab --profile java run --rm --no-deps java
if ($LASTEXITCODE -ne 0) { throw 'No aceptar la validación; revisar fallos y dependencias' }
```

La ejecución Java usa `mvn -o`, Temurin 17 y selecciona solo
UserRepositoryJdbcPostgresIntegrationTest (5),
UserSecurityVersionPostgresIntegrationTest (11) y
AccountInstancePostgresIntegrationTest (3): **19 casos esperados, cero omitidos**.
El runner verifica la versión y proveedor de Java antes de compilar. No aplica
migraciones. No habilitar toda la suite comercial ni conectar credenciales reales.
Si faltan artefactos offline, detenerse y preparar la caché en un proceso separado
autorizado; no abrir red al laboratorio ni instalar dependencias a escondidas.

`validate.psql` rechaza un public no vacío antes de V001 y ejecuta, en orden:
creación de fixtures anteriores al versionado, snapshot temporal, V104/V105/V106,
aserciones de backfill y pruebas SQL dentro de una transacción que hace rollback.
Las dos cuentas y vendedor ficticios históricos permanecen solo en la tmpfs.
No imprime sus hashes; VERBOSITY sqlstate reduce detalles de errores del servidor.

## Matriz de aceptación

| Requisito | Preparado / resultado esperado |
|---|---|
| Backfill | Dos cuentas previas con UUID no nulos/distintos y versión cero; atributos anteriores idénticos. validate.psql. |
| UUID normal | INSERT ignora UUID solicitado; UPDATE diferente o NULL falla 23514; DELETE/reinserción crea otro UUID. SQL y 2 casos Java con ambos roles. |
| Unicidad UUID | Catálogo: columna UUID NOT NULL y constraint UNIQUE sobre esa columna; duplicados nunca sustituyen filas. Java comprueba metadatos y cuerpo de función. No deshabilitar triggers para fabricar duplicados. |
| V104 | 11 casos existentes: campos individuales y conjuntos, NULL, no-op, rename, versión manual, snapshots antiguos y rollback. Barrera verifica CHECK, función y trigger completos. |
| V105 | SQL permite inactivos y NULL; reactivación duplicada falla 23505 del índice exacto. Diagnóstico previo y escenario negativo separado. |
| Sesión | Sesión original rechazada tras recrear ID con misma versión/hash, ambos roles; actualización del snapshot antiguo rechazada; sesión nueva válida y revocable. Java real preparado. |
| Histórico | PBKDF2 1.000 iteraciones, sal 8 bytes, salida 16 bytes; cambio de rol/estado conserva hash byte por byte, UUID y fecha; versión incrementa una vez. Java real preparado. |
| Rollback | Atributos, versión y UUID restaurados en SQL; los 3 casos nuevos Java revierten todos sus datos y no realizan limpieza global. |
| Concurrencia V104 | 2 conexiones y pg_blocking_pids, primer escritor con commit/rollback, tiempos límite y cierre de executor: casos existentes. |
| Concurrencia V105 | Procedimiento de dos terminales descrito abajo, seis combinaciones; no confundir su preparación con ejecución. |
| Recuperación | Duplicados históricos, reaplicación negativa, cierre por error y repetición en laboratorio nuevo según procedimiento siguiente. |

Las nuevas conexiones Java comprueban usuario/marcador, loopback, base, puerto y
versión, después V104 y catálogos V105/V106 antes de fixtures. Comparan el cuerpo
V106 completo con el archivo, aceptando solo normalización CRLF/bordes. Los mocks
y pruebas estáticas no prueban los catálogos reales. Las 5 y 11 integraciones
anteriores conservan sus barreras y solo se habilitan dentro del laboratorio.

## Concurrencia V105: dos terminales y observador

En un laboratorio recién preparado, abrir tres sesiones con el mismo comando
`docker compose -f $lab exec postgres psql -X -w -U identity_validation_owner -d la_exclusiva_test`.
Cada sesión debe ejecutar primero `\i /workspace/database/validation/identity/guard.psql`.
No ejecutar estos fragmentos desde pgAdmin conectado a otro servidor.

Preparación acotada, en una sesión, y fuera de las dos transacciones contendientes:

```sql
UPDATE public.user_account SET active=FALSE,seller_id=NULL
WHERE id IN (910000001,910000002) AND username IN ('iv_pre_a','iv_pre_b');
-- Para reactivación/reactivación: asignar seller_id='iv_seller' a ambas inactivas.
-- Para asignación/reactivación: asignarlo solo a 910000002.
```

Terminal A: `SET application_name='iv_a'; BEGIN;` y activar/asignar el vendedor a
910000001 con `UPDATE public.user_account SET active=TRUE,seller_id='iv_seller'
WHERE id=910000001 AND username='iv_pre_a';`. Mantener la transacción abierta.
Terminal B: `SET application_name='iv_b'; SET lock_timeout='60s';
SET statement_timeout='65s'; BEGIN;` y el mismo UPDATE para 910000002/iv_pre_b.
Debe esperar, sin sleep ni orden temporal supuesto. El observador verifica:

```sql
SELECT cardinality(pg_blocking_pids(pid)) > 0 AS blocked
FROM pg_stat_activity WHERE application_name='iv_b';
```

Solo después de observar blocked=true, A hace COMMIT: B debe fallar 23505 y hacer
ROLLBACK. Repetir en laboratorio nuevo haciendo ROLLBACK en A: B debe completar
su UPDATE, y hacer ROLLBACK para no conservarlo. Repetir ambos desenlaces para
asignación/asignación, reactivación/reactivación y asignación/reactivación.
Si vence el timeout, cerrar y revertir ambas sesiones antes de cualquier limpieza;
el caso falla y no se cuenta como éxito. Ningún executor Java queda vivo en este
procedimiento manual. Registrar los seis resultados y SQLSTATE, sin filas ni hashes.

## Fallos de migración, recuperación y repetibilidad

1. Ejecutar inspect.psql para capturar antes/después solo metadatos: columnas/tipos/defaults, constraint con
   pg_get_constraintdef, índice válido/predicado y trigger/función con definiciones.
   Conservar hashes de migraciones y logs fuera del repo. Nunca exportar cuentas.
2. En un laboratorio nuevo independiente, ejecutar reject-duplicates.psql en lugar
   de validate.psql. Debe terminar no-cero por 23505 al crear V105. En otra sesión,
   guard primero; SELECT de conteos debe confirmar dos fixtures y ausencia del
   índice V105; V104 sigue instalada y V106 no existe. No corregir datos solos.
3. En un laboratorio positivo, probar cada reaplicación como proceso psql separado:
   `psql -X -w ... -v ON_ERROR_STOP=1 -f guard.psql -f V104...sql` (rutas absolutas
   /workspace), luego V105 y V106 de la misma manera. Cada uno debe terminar
   no-cero; una conexión posterior confirma catálogos, UUID y versiones sin cambios.
   No ignorar el error ni usar IF NOT EXISTS para ocultarlo.
4. Cerrar todas las sesiones y contenedores Java. Solo tras revisar con
   `docker compose -f $lab ps -a` que pertenece al proyecto y propósito definidos,
   desechar **ese laboratorio** con `docker compose -f $lab --profile java down`.
   No usar prune, down -v ni comandos contra proyectos ajenos. Eliminar únicamente
   el secreto temporal aprobado; no borrar el repositorio o caché Maven.
5. Repetir up, validate.psql y Java en tmpfs nueva. Comparar resultados y catálogo;
   UUID aleatorios serán distintos, pero no deben variar invariantes ni conteos.
   La repetibilidad procede del entorno nuevo, no de reaplicar DDL al mismo esquema.

## Riesgos y decisiones que Carlos debe confirmar

Confirmar por escrito: equipo Docker/VM, imágenes PostgreSQL 18 y Maven Temurin 17
por digest, disponibilidad de caché Linux, creación/destrucción exclusiva del
laboratorio, permiso para V001/V104/V105/V106 y sus fixtures, 19 integraciones y
los escenarios negativos/concurrentes. Definir quién registra resultados y dónde
se conservan logs sin secretos; Jesús revisará barreras, SQL y privilegios.

El usuario de laboratorio es propietario/superusuario para poder aplicar DDL;
esto no acredita una configuración de privilegios mínima de producción. La
seguridad frente a un dueño que deshabilita triggers, protección real del último
administrador mediante advisory lock y consumidores comerciales que aún no
revalidan sesiones no se declaran validadas por estos casos. La concurrencia de
lectura con revocación conserva el límite documentado de operaciones ya iniciadas.

Aceptar solo con todas las aserciones SQL y 19 casos Java aprobados, cero omitidos,
seis desenlaces V105 registrados, recuperación y segunda ejecución en limpio
verificadas. La ejecución preparada sigue pendiente; esta documentación no es una
autorización ni evidencia de ejecución. No hay cambios de producción en esta tarea.
