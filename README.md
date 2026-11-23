# La Exclusiva P3

Aplicación de escritorio para gestionar catálogo, inventario, compras, ventas, cartera y caja del negocio La Exclusiva. El cliente JavaFX usa Java 17; PostgreSQL/JDBC es opcional y el modo predeterminado conserva almacenamiento en memoria.

## Funciones disponibles

- Inicio de sesión, sesiones revocables, permisos y administración de usuarios.
- Clientes, vendedores, catálogo, inventario, ventas y pagos idempotentes.
- Proveedores, pedidos de compra y recepción integrada al inventario.
- Cartera separada entre deuda de ventas y deuda general; pagos y movimientos de caja.
- Asistente explicable de cartera con seguimiento, recomendaciones, estados de cuenta PDF y avisos semanales sujetos a revisión administrativa.
- Auditoría básica de cambios administrativos y de intentos de envío de estados de cuenta.

El asistente de cartera usa reglas y datos registrados; no es un modelo de aprendizaje automático ni una predicción sobre las personas. `purchase_order` corresponde a compras a proveedores. La aplicación todavía no modela pedidos a domicilio de clientes ni seguimiento de entregas.

## Requisitos y verificación

Se requieren Java 17 y Maven:

```powershell
mvn clean verify
```

En Linux sin servidor gráfico, las pruebas JavaFX pueden ejecutarse con Xvfb:

```bash
xvfb-run -a mvn clean verify
```

## Ejecutar JavaFX

El punto de entrada predeterminado inicia JavaFX:

```powershell
mvn exec:java
```

Por defecto, `LAEXCLUSIVA_PERSISTENCE_MODE` es `memory`, así que los datos de esa ejecución no sobreviven al cierre. En el primer arranque sin cuentas, el modo normal aprovisiona un administrador usando `LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (opcional, por defecto `admin`) y `LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`. La contraseña debe cumplir la política configurada. En PostgreSQL el bootstrap solo ocurre si el repositorio persistente no contiene usuarios; no crea una cuenta alternativa en memoria ante errores de base o esquema.

El modo demo solo está disponible en memoria:

```powershell
$env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD = 'contraseña temporal'
$env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD = 'otra contraseña temporal'
mvn exec:java "-Dexec.args=--demo"
```

No guardes contraseñas reales en el repositorio ni en la línea de comandos compartida. Usa variables privadas del proceso y evita capturar sus valores en registros.

## Seleccionar PostgreSQL

La aplicación utiliza PostgreSQL únicamente cuando se configura `LAEXCLUSIVA_PERSISTENCE_MODE=postgres` junto con `LAEXCLUSIVA_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD`. La configuración inválida o un esquema incompleto debe fallar de forma segura; no se cambia automáticamente a memoria.

Las migraciones no se ejecutan automáticamente. Antes de aplicar una migración, verifica el destino y su versión; nunca uses la base de desarrollo o producción para pruebas. La guía de entorno y el contrato están en [database/README.md](database/README.md) y [database/jdbc-integration-contract.md](database/jdbc-integration-contract.md).

## Asistente de cartera y privacidad

El contrato del tablero y las reglas de estados de cuenta, consentimiento y correo están documentados en [docs/collection-reminders-security.md](docs/collection-reminders-security.md). La simulación de correo está activa por defecto. Un envío SMTP real requiere configuración explícita, consentimiento vigente, revisión individual, horario/días autorizados y confirmación administrativa de las instrucciones de pago. El estado de cuenta es informativo, no es factura electrónica DIAN ni confirma que un pago haya sido recibido.

## Organización

- `src/main/java/com/laexclusiva/model`: modelos y reglas de dominio.
- `src/main/java/com/laexclusiva/repository`: puertos y adaptadores de persistencia.
- `src/main/java/com/laexclusiva/service`: casos de uso, autorización y coordinación transaccional.
- `src/main/java/com/laexclusiva/ui`: composición JavaFX, vistas y controladores.
- `database/migrations`: migraciones SQL versionadas y no automáticas.
- `docs/ai-usage`: bitácoras individuales de asistencia de IA.

## Documentación

- [Guía de PostgreSQL y JDBC](database/README.md)
- [Contrato de integración JDBC](database/jdbc-integration-contract.md)
- [Asistente de cartera, recordatorios y auditoría](docs/collection-reminders-security.md)
- [Bitácoras de uso de IA](docs/ai-usage/README.md)
- [Integración JavaFX de compras](docs/purchases-javafx-integration.md)

Los cambios se proponen mediante Pull Requests a `develop`; no se integra directamente a `main`.
