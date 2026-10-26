# Base de datos

La aplicación utilizará PostgreSQL mediante JDBC.

## Organización prevista

- `schema.sql`: creación versionada del esquema acordado.
- `seed.sql`: datos ficticios de prueba para desarrollo y demostración.

Estos scripts se añadirán cuando el equipo valide las entidades, relaciones y reglas que afectan inventario, ventas y cuentas por cobrar. No se deben crear tablas definitivas antes de resolver las equivalencias de inventario y las reglas de crédito.

## Configuración local

La conexión se configurará por variables de entorno, sin guardar contraseñas en Git.

- `LAEXCLUSIVA_DB_URL`: URL JDBC de la base de datos.
- `LAEXCLUSIVA_DB_USER`: usuario de PostgreSQL.
- `LAEXCLUSIVA_DB_PASSWORD`: contraseña del usuario.

Ejemplo de formato de URL: `jdbc:postgresql://localhost:5432/la_exclusiva`.

Cada integrante debe usar su configuración local. Si se crea un archivo de configuración local, debe quedar excluido de Git. Solo se incluirán en el repositorio ejemplos sin secretos.
