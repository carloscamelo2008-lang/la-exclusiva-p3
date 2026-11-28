# La Exclusiva P3

Aplicación de escritorio para gestionar catálogo, inventario, compras, ventas, cartera y caja del negocio La Exclusiva. El cliente JavaFX usa Java 17 y requiere PostgreSQL local para la operación comercial normal. El almacenamiento en memoria se selecciona explícitamente solo para pruebas o recorridos controlados.

## Funciones disponibles

- Inicio de sesión, sesiones revocables, permisos y administración de usuarios.
- Clientes, vendedores, catálogo, inventario, ventas y pagos idempotentes.
- Proveedores, pedidos de compra y recepción integrada al inventario.
- Cartera separada entre deuda de ventas y deuda general; pagos y movimientos de caja.
- Asistente explicable de cartera con seguimiento, recomendaciones, estados de cuenta PDF y avisos semanales sujetos a revisión administrativa.
- Auditoría básica de cambios administrativos y de intentos de envío de estados de cuenta.

El asistente de cartera usa reglas y datos registrados; no es un modelo de aprendizaje automático ni una predicción sobre las personas. Las nuevas altas de clientes reciben un ID `CLI-<UUID>`; el límite de crédito se retiró de la regla comercial y V113 archiva sus valores históricos. El fiado se controla por saldo de cada venta. La identificación documental aún requiere definición de tipo, obligatoriedad y validación con el negocio. `purchase_order` corresponde a compras a proveedores. La aplicación todavía no modela pedidos a domicilio de clientes ni seguimiento de entregas.

El estado y los límites de la transformación comercial están en [docs/commercial-transformation.md](docs/commercial-transformation.md). No se considera terminada la operación comercial mientras falten los contratos de actor de venta, obligaciones generales individualizadas, cuentas por pagar y los datos reales que debe validar Carlos.

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

El arranque comercial selecciona PostgreSQL por defecto; configure `LAEXCLUSIVA_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` antes de abrir la aplicación. `LAEXCLUSIVA_PERSISTENCE_MODE=memory` solo tiene efecto cuando se elige explícitamente. Si PostgreSQL, sus credenciales o el esquema requerido no están disponibles, la aplicación muestra un error seguro y no cambia a memoria. En el primer arranque de una base vacía, el administrador inicial se aprovisiona con `LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (opcional, por defecto `admin`) y `LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`, que debe cumplir la política. El alta no reemplaza usuarios existentes.

Las fotografías opcionales se almacenan fuera de PostgreSQL en `%USERPROFILE%\La Exclusiva\Productos` (`LAEXCLUSIVA_PRODUCT_IMAGES_DIR`). Los informes PDF se guardan en `%USERPROFILE%\La Exclusiva\Reportes` (`LAEXCLUSIVA_REPORTS_DIR`). Ambas carpetas deben incluirse en el respaldo; el arranque no aplica V113/V114.

La operación comercial no carga datos ficticios. El antiguo cargador de demostración quedó en el alcance de pruebas; las fixtures visuales están en `src/test`. El aprovisionamiento opcional `--demo` crea solo dos cuentas temporales y se limita a memoria seleccionada explícitamente; no forma parte del modo PostgreSQL ni persiste cambios:

```powershell
$env:LAEXCLUSIVA_PERSISTENCE_MODE = 'memory'
$env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD = 'contraseña temporal'
$env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD = 'otra contraseña temporal'
mvn exec:java "-Dexec.args=--demo"
```

No guardes contraseñas reales en el repositorio ni en la línea de comandos compartida. Usa variables privadas del proceso y evita capturar sus valores en registros.

## Seleccionar PostgreSQL

La aplicación utiliza PostgreSQL únicamente cuando se configura `LAEXCLUSIVA_PERSISTENCE_MODE=postgres` junto con `LAEXCLUSIVA_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD`. La configuración inválida o un esquema incompleto debe fallar de forma segura; no se cambia automáticamente a memoria.

Las migraciones no se ejecutan automáticamente. Antes de aplicar una migración, verifica el destino y su versión; nunca uses la base de desarrollo o producción para pruebas. La guía de entorno y el contrato están en [database/README.md](database/README.md) y [database/jdbc-integration-contract.md](database/jdbc-integration-contract.md).

## Asistente de cartera y privacidad

El contrato del tablero y las reglas de seguimiento, estados de cuenta y auditoría están documentados en [docs/collection-reminders-security.md](docs/collection-reminders-security.md). El envío de correo real no está habilitado en la ruta de producción; los datos históricos de avisos permanecen auditables. El estado de cuenta es informativo, no es factura electrónica DIAN ni confirma que un pago haya sido recibido.

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
- [Estado de transformación comercial](docs/commercial-transformation.md)

Los cambios se proponen mediante Pull Requests a `develop`; no se integra directamente a `main`.
