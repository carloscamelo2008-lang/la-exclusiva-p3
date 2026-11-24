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

Las migraciones son SQL versionado, no se ejecutan automáticamente al iniciar la aplicación. El conjunto actual del repositorio incluye `V001`, `V002`, `V004` y `V100`–`V109`; los saltos de numeración son intencionales. La ruta aplicada por el laboratorio se declara en `database/validation/identity/application.psql`; los requisitos de cada versión están en sus encabezados y en el [contrato JDBC](jdbc-integration-contract.md). Antes de aplicar una migración, verifica manualmente el servidor, el nombre de base y el estado de esquema. No repitas una versión ya aplicada. Para pruebas que puedan insertar o limpiar fixtures, utiliza exclusivamente una base ficticia y aislada `la_exclusiva_test` o el laboratorio efímero de CI.

Las migraciones V107 y V108 agregan respectivamente preferencias/seguimiento de cobranza y auditoría administrativa. V109 añade identidad única del intento, propietario de instancia, evidencia cerrada del proveedor y estado de conciliación. También convierte los avisos históricos `SENDING` y `FAILED/PROVIDER_FAILED` a un estado incierto para evitar reenvíos posiblemente duplicados. Dependen de las tablas de cliente e identidad existentes. No alteran el historial de clientes, ventas ni pagos. No se ejecutaron migraciones locales como parte de esta implementación.

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

## Protección de datos

No registrar contraseñas, cadenas de conexión completas, correos de clientes ni contenidos de documentos. La bitácora administrativa guarda actor, acción, objetivo, fecha y resultado cerrado, no hashes, tokens, SQL ni secretos. El correo real permanece deshabilitado por defecto. Las pruebas no envían comunicaciones reales.
