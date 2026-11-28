# PostgreSQL y JDBC

La operación comercial usa PostgreSQL mediante JDBC y falla cerrada si faltan configuración, conexión o esquema. El modo `memory` se reserva para pruebas y recorridos controlados explícitamente; nunca se elige por omisión. Las conexiones productivas y de pruebas usan variables distintas para reducir el riesgo de apuntar a una base equivocada.

## Configuración

| Variable | Uso |
|---|---|
| `LAEXCLUSIVA_PERSISTENCE_MODE` | `postgres` (predeterminado); `memory` únicamente por selección explícita |
| `LAEXCLUSIVA_DB_URL` | URL de la base a la que se conectará la aplicación |
| `LAEXCLUSIVA_TEST_DB_URL` | URL exclusiva para pruebas de integración; valida el nombre `la_exclusiva_test` |
| `LAEXCLUSIVA_DB_USER` | Usuario PostgreSQL |
| `LAEXCLUSIVA_DB_PASSWORD` | Contraseña PostgreSQL, solo en el entorno del proceso |
| `LAEXCLUSIVA_PRODUCT_IMAGES_DIR` | Carpeta local persistente de fotografías PNG/JPEG content-addressed |
| `LAEXCLUSIVA_REPORTS_DIR` | Carpeta local persistente de estados de cuenta PDF |

Ejemplos de nombres de base (no copiar una URL de desarrollo al proceso de pruebas):

```text
Aplicación local: jdbc:postgresql://localhost:5432/la_exclusiva
Pruebas locales:  jdbc:postgresql://localhost:5432/la_exclusiva_test
```

En PowerShell, conserva la contraseña fuera del historial y no la escribas en documentos:

```powershell
$env:LAEXCLUSIVA_PERSISTENCE_MODE = 'postgres'
$env:LAEXCLUSIVA_DB_URL = 'jdbc:postgresql://localhost:5432/la_exclusiva'
$env:LAEXCLUSIVA_DB_USER = 'usuario_local'
# Carga LAEXCLUSIVA_DB_PASSWORD desde un almacén privado del usuario.
```

Para las pruebas automatizadas, define `LAEXCLUSIVA_TEST_DB_URL` con la base aislada `la_exclusiva_test` y las variables de usuario/contraseña en el proceso Maven. Las pruebas PostgreSQL optativas se omiten si falta la URL; si se proporciona otro nombre de base, deben fallar antes de ejecutar SQL. Nunca uses datos reales en los fixtures.

## Migraciones

Las migraciones son SQL versionado, no se ejecutan automáticamente al iniciar la aplicación. El conjunto actual del repositorio incluye `V001`, `V002`, `V004` y `V100`–`V114`; los saltos de numeración son intencionales. La ruta aplicada por el laboratorio se declara en `database/validation/identity/application.psql`; los requisitos de cada versión están en sus encabezados y en el [contrato JDBC](jdbc-integration-contract.md). Antes de aplicar una migración, verifica manualmente el servidor, el nombre de base y el estado de esquema. No repitas una versión ya aplicada. Para pruebas que puedan insertar o limpiar fixtures, utiliza exclusivamente una base ficticia y aislada `la_exclusiva_test` o el laboratorio efímero de CI.

Las migraciones V107 y V108 agregan respectivamente preferencias/seguimiento de cobranza y auditoría administrativa. V109 añade identidad única del intento, propietario de instancia, evidencia cerrada del proveedor y estado de conciliación. También convierte los avisos históricos `SENDING` y `FAILED/PROVIDER_FAILED` a un estado incierto para evitar reenvíos posiblemente duplicados. Dependen de las tablas de cliente e identidad existentes. No alteran el historial de clientes, ventas ni pagos. No se ejecutaron migraciones locales como parte de esa implementación.

V110 normaliza los teléfonos de clientes a dígitos ASCII, elimina espacios y el prefijo `+` de presentación, y crea el índice único `uq_customer_phone`. Antes de aplicarla, revisa los formatos históricos inválidos o los números que coincidan tras normalizar: la migración se revierte por completo si encuentra alguno. Las direcciones pueden repetirse.

V111 agregó cupos que fueron utilizados por una versión anterior para limitar ventas a crédito. V113 renombra esa columna a `legacy_credit_limit` y conserva sus valores; el campo deja de exponerse en el modelo y no limita el fiado nuevo, que se controla por el saldo de cada venta. El índice de ID normalizado conserva las claves históricas y requiere resolver previamente identificaciones equivalentes por espacios.

V112 agrega vendedores activos, responsables de operaciones y el medio de pago de caja. Las filas históricas conservan `NULL` cuando no consta el actor o el medio. En caja manual se registra el medio elegido en la interfaz.

V113 archiva sin borrar los valores existentes de cupo del cliente y renombra su restricción. No cambia IDs, clientes, ventas, saldos ni pagos. El arranque no la aplica: inclúyala en una ventana controlada de mantenimiento antes de actualizar el entorno comercial.

V114 agrega una referencia opcional y validada a la fotografía local del producto. El archivo se guarda fuera de la base en el almacén portable configurado por `LAEXCLUSIVA_PRODUCT_IMAGES_DIR`; el arranque no aplica migraciones ni crea imágenes ficticias.

## Respaldo y restauración

Haz un respaldo antes de una migración y durante una ventana con la aplicación cerrada. Guarda los archivos fuera del repositorio, en una ubicación con acceso restringido. `pg_dump -Fc` produce un archivo de formato custom; `-W` solicita la contraseña de forma interactiva, por lo que no se incluye en el comando ni en el historial:

```powershell
pg_dump -Fc -h localhost -p 5432 -U usuario_local -W -d la_exclusiva `
  -f 'D:\Respaldos\la_exclusiva-AAAA-MM-DD.dump'
pg_restore --list 'D:\Respaldos\la_exclusiva-AAAA-MM-DD.dump'
```

La restauración de prueba debe hacerse en **una base nueva y vacía con otro nombre**, nunca sobre la base existente. Cree el destino solo tras verificar que ese nombre no existe y que el servidor es el local autorizado. No use `--clean`, `--create` ni comandos que eliminen objetos. Después restaure y compruebe las tablas con lectura:

```powershell
createdb -h localhost -p 5432 -U usuario_local -W la_exclusiva_restore_AAAA_MM_DD
pg_restore -h localhost -p 5432 -U usuario_local -W `
  --no-owner --no-privileges -d la_exclusiva_restore_AAAA_MM_DD `
  'D:\Respaldos\la_exclusiva-AAAA-MM-DD.dump'
psql -h localhost -p 5432 -U usuario_local -W -d la_exclusiva_restore_AAAA_MM_DD `
  -c "SELECT current_database(), count(*) FROM public.customer"
```

El archivo PostgreSQL no incluye fotografías ni PDFs. Respalda por separado las carpetas configuradas por `LAEXCLUSIVA_PRODUCT_IMAGES_DIR` y `LAEXCLUSIVA_REPORTS_DIR` con la aplicación cerrada; conserva sus permisos y confidencialidad. Una restauración real debe ensayarse primero en el destino aislado y revisarse por Carlos. Este procedimiento está documentado, pero **esta rama no ejecutó `pg_dump`, `createdb` ni `pg_restore`**.

## Capas de persistencia

- `DatabaseConfig` valida configuración y separa URL de aplicación y pruebas.
- `JdbcConnectionProvider` comparte la conexión transaccional activa.
- `JdbcTransactionManager` coordina commit, rollback y lecturas consistentes.
- Repositorios JDBC encapsulan SQL parametrizado; los servicios conservan las reglas de negocio.
- `ApplicationServices` construye la composición elegida. En PostgreSQL identidad, compras, ventas, cartera, caja, seguimiento y auditoría comparten el proveedor JDBC cuando el contrato lo requiere.

Ejecutar verificación automatizada:

```powershell
mvn clean verify
```

Las pruebas unitarias no requieren PostgreSQL. La lista de suites optativas y la última evidencia real deben consultarse en Surefire y en la documentación de validación; `BUILD SUCCESS` por sí solo no indica que las integraciones se hayan ejecutado.

El workflow `identity-postgres` restaura la caché Maven Java 17 de GitHub Actions (`~/.m2/repository`), compartida por clave con el build Maven del PR, y copia solo ese directorio a la ruta temporal única del job. El laboratorio rechaza enlaces simbólicos y no copia `settings.xml` ni credenciales. El contenedor de preparación puede completar dependencias faltantes antes de crear PostgreSQL; después, los contenedores de pruebas montan el seed Maven en solo lectura, lo copian a su `/root/.m2` temporal y ejecutan Maven con `-o`. Así los ciclos PostgreSQL no descargan dependencias ni dependen de una conexión a Maven Central. El cache se limita a artefactos de build: no comparte la base, la red, secretos o sistema de archivos de PostgreSQL. No se agregan reintentos ciegos para HTTP 429; se reduce la descarga repetida reutilizando dependencias ya resueltas.

### Validación del laboratorio del PR #27

En el HEAD `ca6ddf9f84fef7392fc5b3b11709f92871062d03`, el workflow [Identity and purchases PostgreSQL laboratory, run 45302806390](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45302806390) terminó `success`; Maven Build también pasó en los eventos `pull_request` y `push`. El job ejecutó 113 pruebas PostgreSQL: identidad 26/26 en cada uno de dos ciclos, aplicación 60/60 y esquema incompleto 1/1; cero fallidas, errores u omitidas. El total está respaldado por aserciones de cantidad y resultado en `actions-lab.py`, no solo por el estado `BUILD SUCCESS`.

La ruta del laboratorio aplicó V109 después de las migraciones previas y comprobó la conversión de fixtures históricos `SENDING` y `FAILED/PROVIDER_FAILED` a conciliación requerida. El test PostgreSQL de conciliación SMTP y recuperación tras reinicio formó parte del conjunto de aplicación; la prueba de fixture que había fallado por el orden de limpieza pasó en esta ejecución. El job concluyó correctamente, ejecutó su paso de limpieza y publicó el artefacto sanitizado. PostgreSQL 18 y Java 17 se ejecutaron con imágenes fijadas por digest en un laboratorio efímero; no se usaron bases locales ni se envió SMTP real.

La causa de dos fallos anteriores fue HTTP 429 al descargar `maven-resources-plugin:3.4.0` de Maven Central, antes de comenzar las pruebas PostgreSQL. Se estabilizó el uso de dependencias restaurando la caché Maven del build de GitHub Actions y copiando solo `~/.m2/repository` a un directorio temporal único del job. Los contenedores de pruebas usan una copia montada en solo lectura y Maven offline (`-o`) después de la preparación. No se añadieron reintentos de red, se omitieron pruebas ni se compartieron credenciales o estado de base. La ejecución final no reprodujo HTTP 429.

## Protección de datos

No registrar contraseñas, cadenas de conexión completas, correos de clientes ni contenidos de documentos. La bitácora administrativa guarda actor, acción, objetivo, fecha y resultado cerrado, no hashes, tokens, SQL ni secretos. El correo real permanece deshabilitado por defecto. Las pruebas no envían comunicaciones reales.
