# PostgreSQL y JDBC

PostgreSQL es la persistencia prevista para La Exclusiva P3. La infraestructura JDBC utiliza Java 17, JDBC estándar y el controlador PostgreSQL.

## Bases locales

Crear dos bases independientes:

- `la_exclusiva`: desarrollo local.
- `la_exclusiva_test`: pruebas de integración, solo con datos ficticios.

Ejecutar `database/migrations/V001__initial_schema.sql` una sola vez desde pgAdmin Query Tool, conectado a cada base vacía. La migración crea las diez tablas, restricciones e índices. No carga productos ni existencias iniciales.

## Variables de entorno

Configurar estas variables en el proceso que ejecuta Maven:

| Variable | Uso |
|---|---|
| `LAEXCLUSIVA_DB_URL` | URL de la base local de desarrollo |
| `LAEXCLUSIVA_TEST_DB_URL` | URL separada para pruebas de integración |
| `LAEXCLUSIVA_DB_USER` | Usuario local PostgreSQL |
| `LAEXCLUSIVA_DB_PASSWORD` | Contraseña local PostgreSQL |

Desarrollo:

```text
jdbc:postgresql://localhost:5432/la_exclusiva
```

Pruebas:

```text
jdbc:postgresql://localhost:5432/la_exclusiva_test
```

Ejemplo PowerShell para desarrollo:

```powershell
$env:LAEXCLUSIVA_DB_URL = "jdbc:postgresql://localhost:5432/la_exclusiva"
$env:LAEXCLUSIVA_DB_USER = "postgres"
$env:LAEXCLUSIVA_DB_PASSWORD = "TU_PASSWORD_LOCAL"
```

Ejemplo PowerShell para pruebas:

```powershell
$env:LAEXCLUSIVA_TEST_DB_URL = "jdbc:postgresql://localhost:5432/la_exclusiva_test"
$env:LAEXCLUSIVA_DB_USER = "postgres"
$env:LAEXCLUSIVA_DB_PASSWORD = "TU_PASSWORD_LOCAL"
```

Se reutilizan usuario y contraseña, pero nunca la URL. Las pruebas unitarias no requieren variables ni PostgreSQL. Las pruebas de integración se omiten si falta `LAEXCLUSIVA_TEST_DB_URL`; si está definida, `DatabaseConfig.fromTestEnvironment()` comprueba que la base de destino sea exactamente `la_exclusiva_test`.

No guardar contraseñas en Git, README, pruebas ni bitácoras. El proveedor no imprime la contraseña. Nunca ejecutar limpieza de datos de prueba sobre `la_exclusiva`.

## Clases JDBC

- `DatabaseConfig`: valida y carga la configuración de desarrollo o pruebas.
- `JdbcConnectionProvider`: entrega una conexión por operación o reutiliza la conexión transaccional activa.
- `JdbcTransactionManager`: coordina un único commit/rollback para la operación completa.
- `SqlFunction<T>`: callback JDBC utilizado por `withConnection`.
- `JdbcPersistenceException`: excepción no comprobada para errores SQL.

Consultar [el contrato de integración JDBC](jdbc-integration-contract.md) antes de implementar DAO o límites transaccionales.

## Ejecución

```bash
mvn clean verify
```

La aplicación principal seguirá utilizando repositorios en memoria hasta que se complete la implementación y composición de los DAO.
