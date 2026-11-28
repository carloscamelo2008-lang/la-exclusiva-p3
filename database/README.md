# PostgreSQL y JDBC

La aplicación puede usar PostgreSQL mediante JDBC en modo explícito. JavaFX y el modo `memory` siguen disponibles sin conexión a la base. Las conexiones productivas y de pruebas usan variables distintas para reducir el riesgo de apuntar a una base equivocada.

## Configuración

| Variable | Uso |
|---|---|
| `LAEXCLUSIVA_PERSISTENCE_MODE` | `memory` (predeterminado) o `postgres` para la aplicación |
| `LAEXCLUSIVA_DB_URL` | URL de la base a la que se conectará la aplicación |
| `LAEXCLUSIVA_TEST_DB_URL` | URL exclusiva para pruebas de integración; valida el nombre `la_exclusiva_test` |
| `LAEXCLUSIVA_DB_USER` | Usuario PostgreSQL |
| `LAEXCLUSIVA_DB_PASSWORD` | Contraseña PostgreSQL, solo en el entorno del proceso |

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

### Instalación comercial y rol aplicativo

En una base local nueva, con el esquema `public` vacío, confirma el servidor, puerto,
base y rol instalador. Desde la raíz del repositorio ejecuta:

```powershell
psql -X -h localhost -p 5432 -U rol_instalador -d la_exclusiva -f database/deployment/install.psql
```

El instalador aplica las 16 migraciones en orden y registra el conjunto en
`public.schema_migration`. La cobertura actual de `develop` llega hasta
`V112`. La versión comercial final depende de incorporar `V113` y `V114`
de la rama de transformación comercial. Consulta [el procedimiento de respaldo
y actualización](migrations/README.md) antes de intervenir una base existente.

Como administrador PostgreSQL, crea un rol de inicio de sesión dedicado, sin
superusuario, creación de bases, creación de roles ni membresía en el rol dueño.
Asigna la contraseña interactivamente con `\password la_exclusiva_app` en `psql` y
proporciónala al proceso de aplicación mediante un almacén privado. El rol
instalador conserva la propiedad y el DDL. Este ejemplo usa la base `la_exclusiva`
y debe ejecutarse conectado a ella:

```sql
CREATE ROLE la_exclusiva_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT CONNECT ON DATABASE la_exclusiva TO la_exclusiva_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO la_exclusiva_app;

GRANT SELECT ON public.schema_migration TO la_exclusiva_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.customer TO la_exclusiva_app;
GRANT SELECT, INSERT, UPDATE ON public.user_account, public.seller,
    public.product_price, public.inventory_item, public.supplier, public.purchase_order,
    public.purchase_order_line, public.customer_collection_profile,
    public.customer_collection_reminder TO la_exclusiva_app;
GRANT SELECT, INSERT ON public.beer_product, public.sale, public.sale_line,
    public.sale_payment, public.customer_general_debt_movement, public.stock_movement,
    public.cash_movement, public.goods_receipt, public.goods_receipt_line,
    public.customer_collection_follow_up, public.administrative_audit_event TO la_exclusiva_app;
GRANT UPDATE (id) ON public.sale TO la_exclusiva_app;
```

| Tabla en `public` | Permisos requeridos | Uso JDBC |
|---|---|---|
| `schema_migration` | SELECT | Comprobar la versión instalada |
| `customer` | SELECT, INSERT, UPDATE, DELETE | Mantener clientes y saldos, y eliminar según las reglas del repositorio |
| `user_account` | SELECT, INSERT, UPDATE | Gestionar cuentas, estado y credenciales |
| `seller` | SELECT, INSERT, UPDATE | Registrar, consultar y cambiar el estado |
| `beer_product` | SELECT, INSERT | Registrar productos y consultar el catálogo |
| `product_price` | SELECT, INSERT, UPDATE | Registrar y modificar precios |
| `inventory_item` | SELECT, INSERT, UPDATE | Crear, consultar y ajustar existencias |
| `stock_movement` | SELECT, INSERT | Registrar y consultar movimientos de inventario |
| `sale` | SELECT, INSERT, UPDATE (id) | Guardar y consultar ventas y bloquear su cabecera al registrar abonos |
| `sale_line` | SELECT, INSERT | Guardar y consultar líneas de venta |
| `sale_payment` | SELECT, INSERT | Registrar y consultar abonos de venta |
| `customer_general_debt_movement` | SELECT, INSERT | Registrar y consultar movimientos de cartera |
| `cash_movement` | SELECT, INSERT | Registrar movimientos y consultar caja |
| `supplier` | SELECT, INSERT, UPDATE | Mantener proveedores |
| `purchase_order` | SELECT, INSERT, UPDATE | Crear pedidos y cambiar su estado |
| `purchase_order_line` | SELECT, INSERT, UPDATE | Registrar líneas y ajustar las unidades recibidas |
| `goods_receipt` | SELECT, INSERT | Registrar y consultar recepciones |
| `goods_receipt_line` | SELECT, INSERT | Registrar y consultar detalles de recepción |
| `customer_collection_profile` | SELECT, INSERT, UPDATE | Mantener preferencias de cobranza |
| `customer_collection_reminder` | SELECT, INSERT, UPDATE | Registrar avisos y actualizar su estado de entrega |
| `customer_collection_follow_up` | SELECT, INSERT | Registrar y consultar seguimientos |
| `administrative_audit_event` | SELECT, INSERT | Registrar y consultar eventos de auditoría |

`UPDATE (id)` permite el `SELECT … FOR UPDATE` de Ventas. PostgreSQL también
permite actualizar esa columna con la misma concesión. El repositorio la
utiliza únicamente para bloquear la fila y serializar los abonos.
El borrado de `user_account` que implementa el repositorio solo tiene un
consumidor en la compensación del alta de cuentas demo en memoria.

Si el rol ya recibió concesiones más amplias, revoca las sobrantes antes de
aplicar esta matriz. `GRANT` agrega permisos y deja vigentes los anteriores.
Comprueba también permisos efectivos heredados de `PUBLIC` u otras membresías.

`PostgresStartupVerifier.verify()` valida la conexión, las tablas y columnas
requeridas, el registro V112 o posterior y los permisos mínimos del usuario
efectivo. El PR 1 entrega la infraestructura JDBC de invocación explícita.
El arranque actual de `App` y `ApplicationServices` todavía carece de esta
verificación automática. Su activación obligatoria antes de habilitar la
sesión queda para el PR 2 (`feature/jesus-commercial-startup`). Los laboratorios
de integración actuales aplican migraciones sin crear `schema_migration`.

Las migraciones son SQL versionado, no se ejecutan automáticamente al iniciar la aplicación. El conjunto actual del repositorio incluye `V001`, `V002`, `V004` y `V100`–`V112`; los saltos de numeración son intencionales. La ruta aplicada por el laboratorio se declara en `database/validation/identity/application.psql`; los requisitos de cada versión están en sus encabezados y en el [contrato JDBC](jdbc-integration-contract.md). Antes de aplicar una migración, verifica manualmente el servidor, el nombre de base y el estado de esquema. No repitas una versión ya aplicada. Para pruebas que puedan insertar o limpiar fixtures, utiliza exclusivamente una base ficticia y aislada `la_exclusiva_test` o el laboratorio efímero de CI.

Las migraciones V107 y V108 agregan respectivamente preferencias/seguimiento de cobranza y auditoría administrativa. V109 añade identidad única del intento, propietario de instancia, evidencia cerrada del proveedor y estado de conciliación. También convierte los avisos históricos `SENDING` y `FAILED/PROVIDER_FAILED` a un estado incierto para evitar reenvíos posiblemente duplicados. Dependen de las tablas de cliente e identidad existentes. No alteran el historial de clientes, ventas ni pagos. No se ejecutaron migraciones locales como parte de esa implementación.

V110 normaliza los teléfonos de clientes a dígitos ASCII, elimina espacios y el prefijo `+` de presentación, y crea el índice único `uq_customer_phone`. Antes de aplicarla, revisa los formatos históricos inválidos o los números que coincidan tras normalizar: la migración se revierte por completo si encuentra alguno. Las direcciones pueden repetirse.

V111 agrega el cupo de crédito del cliente con valor inicial cero. Una venta con saldo pendiente exige que la deuda agregada más el nuevo importe financiado no supere el cupo configurado. El índice de ID normalizado conserva las claves históricas y requiere resolver previamente identificaciones equivalentes por espacios.

V112 agrega vendedores activos, responsables de operaciones y el medio de pago de caja. Las filas históricas conservan `NULL` cuando no consta el actor o el medio. En caja manual se registra el medio elegido en la interfaz.

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
