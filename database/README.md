# PostgreSQL: persistencia definitiva en una etapa posterior

PostgreSQL es el motor previsto para la persistencia definitiva. En el primer avance, la prioridad es construir y probar los casos de uso de consola; se utilizarán colecciones en memoria detrás de contratos de repositorio. Los datos de ese avance se reinician al cerrar el programa.

## Alcance de la siguiente etapa

- Diseñar el modelo entidad-relación.
- Crear schema.sql y datos ficticios de prueba.
- Implementar repositorios/DAO mediante JDBC.
- Configurar transacciones para guardar una venta, líneas, pagos y ajustes de inventario de manera consistente.
- Implementar CRUD y manejo de errores de persistencia.
- Probar restricciones e integridad de las operaciones.

## Configuración local prevista

No guardar credenciales en Git. La conexión utilizará variables de entorno:
- LAEXCLUSIVA_DB_URL
- LAEXCLUSIVA_DB_USER
- LAEXCLUSIVA_DB_PASSWORD

Formato de URL de ejemplo: jdbc:postgresql://localhost:5432/la_exclusiva.

Usar datos ficticios en scripts y pruebas. No copiar al repositorio teléfonos, direcciones ni saldos reales de clientes.
