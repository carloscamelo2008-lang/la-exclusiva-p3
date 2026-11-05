# PostgreSQL y JDBC

PostgreSQL es la persistencia prevista para La Exclusiva P3. La infraestructura JDBC utiliza Java 17, JDBC estándar y el controlador PostgreSQL.

## Crear las bases locales

Crear por separado las bases:

- la_exclusiva: desarrollo local.
- la_exclusiva_test: pruebas de integración. No usar datos reales.

Ejecutar database/migrations/V001__initial_schema.sql una sola vez desde pgAdmin Query Tool, conectado a la base correspondiente. La migración crea las tablas, restricciones e índices. No carga productos ni existencias iniciales.

## Variables de entorno

Configurar estas variables en el proceso que ejecuta Maven:

- LAEXCLUSIVA_DB_URL
- LAEXCLUSIVA_DB_USER
- LAEXCLUSIVA_DB_PASSWORD

URL de desarrollo:

```text
jdbc:postgresql://localhost:5432/la_exclusiva
```

URL de pruebas:

```text
jdbc:postgresql://localhost:5432/la_exclusiva_test
```

Ejemplo PowerShell para desarrollo:

```powershell
$env:LAEXCLUSIVA_DB_URL = "jdbc:postgresql://localhost:5432/la_exclusiva"
$env:LAEXCLUSIVA_DB_USER = "postgres"
$env:LAEXCLUSIVA_DB_PASSWORD = "TU_PASSWORD_LOCAL"
```

No guardar contraseñas en Git, README, pruebas ni bitácoras. El proveedor no imprime la contraseña.

## Clases JDBC

- DatabaseConfig: valida y carga las variables de entorno.
- JdbcConnectionProvider: entrega una conexión por operación o reutiliza la conexión transaccional activa.
- JdbcTransactionManager: coordina un único commit/rollback para la operación completa.
- SqlFunction<T>: callback JDBC utilizado por withConnection.
- JdbcPersistenceException: excepción no comprobada para errores SQL.

Consultar database/jdbc-integration-contract.md antes de implementar DAO o límites transaccionales.

## Ejecución

```bash
mvn clean verify
```

La configuración JDBC ya puede construirse desde variables de entorno. La aplicación principal seguirá usando repositorios en memoria hasta que se complete la implementación y composición de los DAO.
