# Integración JavaFX de Compras y Recepción

## Composición de servicios

`ApplicationServices` expone `purchases()` y construye el módulo junto con la
composición elegida por `LAEXCLUSIVA_PERSISTENCE_MODE`:

- En modo `memory`, Compras comparte el `ProductService`, el repositorio de
  existencias, el historial de movimientos, el `UserRepository` y el
  `AuthorizationService` de la aplicación.
- En modo `postgres`, los adaptadores de proveedores, pedidos, recepciones,
  identidad, catálogo, inventario y movimientos usan el mismo
  `JdbcConnectionProvider`. La recepción participa en la misma frontera
  transaccional que actualiza sus existencias e historial.

Crear un pedido no modifica existencias. Solo una recepción confirmada aumenta
el stock, crea el movimiento `ENTRY` y actualiza las cantidades recibidas del
pedido. La capacidad por canasta guardada al emitir el pedido sigue siendo la
referencia histórica de las conversiones.

## Navegación y contrato para la modernización visual

El menú JavaFX expone el botón `fx:id="btnPurchases"` y la acción
`onAction="#handleShowPurchases"`. El controlador monta la vista cargada por
`PurchasesController.load(...)` en el centro del `BorderPane`. Esta es la
integración funcional mínima; no introduce estilos ni rediseña el dashboard.

Al incorporar el diseño definitivo, la pantalla puede cambiar su presentación,
pero debe conservar un punto de entrada equivalente en el menú, un contenedor
de navegación central y el enrutamiento al módulo con los servicios y autoridad
compartidos. La visibilidad del acceso usa `VIEW_INVENTORY`; los servicios
vuelven a validar ese permiso para las consultas y
`MANAGE_INVENTORY_ENTRIES` para altas, cambios de proveedor, pedidos,
cancelaciones y recepciones. Ocultar un botón no sustituye esas comprobaciones.

## Identidad y auditoría

Antes de guardar un pedido o una recepción, el servicio obtiene el principal
validado por la autoridad actual y resuelve la cuenta persistida en el
`UserRepository` compartido. `created_by_user_id` y `received_by_user_id` guardan
ese ID, junto con el nombre actual como dato histórico. Si la cuenta ya no
existe o su versión, rol o identidad de cuenta no coincide con la sesión, la
operación falla antes de registrar la auditoría.

## Atomicidad, reintentos y diferencias entre modos

En PostgreSQL, la recepción bloquea el pedido y revisa su estado, saldo e
idempotencia bajo la unidad de trabajo JDBC compartida. El registro de la
recepción, sus líneas, los movimientos, el incremento del inventario y el
progreso del pedido se confirman juntos. Si falla un paso, la transacción
revierte el conjunto. La clave de idempotencia está limitada al pedido: una
repetición con la misma clave y carga devuelve la recepción original; reutilizar
la clave con líneas o cantidades distintas se rechaza. El bloqueo y la
validación se repiten al ejecutar cada solicitud concurrente.

El modo `memory` comparte repositorios con Inventario y serializa las
operaciones de la composición, pero no es durable y no ofrece rollback general
ante un fallo en mitad de una recepción. Sus garantías no se presentan como
equivalentes a la transacción PostgreSQL.

## Esquema y laboratorio PostgreSQL

El esquema de aplicación requiere V001 antes de V004; el laboratorio efímero de
esta rama también aplica las migraciones comerciales necesarias y V104, V105 y
V106 para identidad. La ruta de `workflow_dispatch` y el PR mismo restringen
las conexiones a un contenedor PostgreSQL temporal llamado
`la_exclusiva_test`, en una red interna sin puertos publicados y con imágenes
Java 17/PostgreSQL fijadas por digest. El laboratorio comprueba la rama y el
HEAD del PR, ejecuta las suites reales y elimina solo los contenedores/redes de
su proyecto temporal. No se usa ni migra una base local, compartida o de
producción.

Validación local de esta rama: Microsoft JDK 17.0.18, Maven 3.10.0,
`mvn clean verify` — **821 casos contabilizados, 762 aprobados, 0 fallidos,
0 errores y 59 omitidos**. Las omisiones corresponden a integraciones
PostgreSQL optativas, pues este proceso local no recibió configuración de base;
no se cuentan como aprobadas. La suite dirigida de composición, servicio y
controlador de Compras pasó **14/14**. La ejecución completa generó el JAR.
Maven mostró advertencias no bloqueantes del modelo efectivo de JavaFX y
JavaFX indicó en las pruebas que sus clases se cargaron desde un módulo sin
nombre. Las trazas de fallo de consulta/logout de las pruebas son escenarios
simulados; todas las pruebas correspondientes pasaron.

El laboratorio efímero se ejecutó en GitHub Actions sobre el HEAD
`596db429e965976ac59b4a86b7a25b364a3d9b5d`. Usó PostgreSQL 18 con la imagen
fijada por digest `postgres:18-bookworm@sha256:194e55feb2597b1465dbee81cc9fd8d204566cd2eb42c8d22fd6c931a38781b1`, Temurin Java 17.0.20.1 y Maven 3.9.16. Resultado: **111/111 pruebas
aprobadas, 0 fallidas, 0 errores y 0 omitidas**:

- Identidad: 26/26 en el primer ciclo y 26/26 en la repetición.
- Aplicación y flujos comerciales: 58/58 en 15 suites, incluyendo las 6 pruebas
  reales de `PurchaseWorkflowPostgresIntegrationTest`.
- Esquema de identidad incompleto: 1/1; la composición rechaza el esquema.
- Las comprobaciones SQL de recuperación, restricciones y reintento de
  migraciones también pasaron.

El escenario comercial aplicó solo en el contenedor temporal V001, V004, V002,
V100, V101, V102, V103, V104, V105 y V106. Verificó altas de proveedores y
pedidos, recepciones, stock/movimientos, actor persistido, idempotencia,
concurrencia, rollback, compatibilidad de caja y rechazo de la sesión anterior
después de cambiar la asignación de vendedor. La prueba autentica una sesión
nueva para continuar el flujo; no se debilitó la revocación. El primer intento
había obtenido 57/58 en aplicación porque usaba esa sesión revocada; se corrigió
el fixture y el segundo run pasó completo.

El job confirmó `completed=true` y `destroyed=true`. Evidencia:
[run 45217400054](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45217400054), artefacto sanitizado `identity-postgres-45217400054-1` (ID
`14840065283`). El artefacto confirma `source_head` igual al HEAD probado y los
totales Surefire. El run de aplicación anterior y su corrección se conservan en
la bitácora de IA.

## Lista breve de revisión manual en Windows

Las pruebas automatizadas cargan FXML y accionan controladores, pero no equivalen
a una inspección visual manual. En Windows, después de elegir el diseño final,
comprobar:

1. Iniciar sesión como administrador; abrir **Compras y Recepción** desde el
   menú y comprobar que carga el catálogo de proveedores y pedidos.
2. Crear un proveedor y un pedido de varias canastas; verificar que emitir el
   pedido no aumenta existencias.
3. Recibir primero una parte y luego el saldo restante; confirmar el estado
   parcial/completo, el stock y los movimientos mostrados.
4. Reintentar una recepción confirmada con la misma clave; confirmar que no
   duplica stock. Probar una carga diferente con esa clave y verificar el
   rechazo.
5. Repetir consultas con un trabajador y confirmar lectura sin controles de
   escritura; intentar la operación de escritura y comprobar su rechazo.
6. Cerrar sesión, volver a iniciar sesión y verificar los pedidos/recepciones
   persistidos en modo PostgreSQL.

## Límites y preparación de entrega

El módulo de compras no registra pagos a proveedores ni movimientos de caja:
una recepción representa mercancía recibida, no un egreso de efectivo. La
modernización visual de Daniel y el ícono oficial siguen pendientes. Por ello
no se prepara todavía un instalador de Windows; primero se requiere la revisión
visual manual y la aprobación del diseño y el ícono.
