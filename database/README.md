# PostgreSQL: etapa de persistencia definitiva

PostgreSQL es el motor previsto para la etapa de persistencia definitiva del proyecto. De acuerdo con la prioridad de este avance, todavía no se han implementado el esquema ni las clases JDBC.

## Alcance de la etapa posterior

- Definir el modelo entidad-relación.
- Preparar schema.sql y datos ficticios de prueba.
- Implementar repositorios/DAO usando JDBC.
- Configurar transacciones para registrar ventas, líneas, pagos y ajustes de inventario de manera consistente.
- Probar altas, consultas, actualizaciones, restricciones y errores de persistencia.

## Configuración local

No guardar credenciales en Git. Cuando comience la integración se utilizarán variables de entorno como:

- LAEXCLUSIVA_DB_URL
- LAEXCLUSIVA_DB_USER
- LAEXCLUSIVA_DB_PASSWORD

Formato de URL de ejemplo: jdbc:postgresql://localhost:5432/la_exclusiva.

## Datos

Solo se deben usar datos ficticios en los scripts y pruebas del repositorio. No se deben copiar nombres, teléfonos, direcciones o saldos reales de clientes del negocio.
