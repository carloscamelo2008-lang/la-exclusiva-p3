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

## Recorrido JavaFX observado en Windows

El 23/11/2026 se abrió la aplicación con Microsoft JDK 17.0.18 y Maven 3.10.0,
configurada explícitamente con `LAEXCLUSIVA_PERSISTENCE_MODE=memory`. No se
configuró URL JDBC ni se conectó a una base local. Se usaron cuentas y datos
ficticios exclusivamente en memoria; al cerrar la aplicación desaparecieron.
No se registró la contraseña temporal de prueba.

- El administrador temporal `qa.admin` inició sesión, abrió **Compras y
  Recepción** y registró el proveedor `QA Temporary Supplier 20261123`, con
  identificador ficticio `QA-20261123-001`.
- Emitió el pedido 1 de una canasta de `COSTENITA_175` (38 unidades). La emisión
  no alteró el stock. Dos recepciones de 19 unidades mostraron sucesivamente
  `PARTIAL` y `COMPLETED`; el historial atribuyó ambas a `qa.admin` y mostró
  claves diferentes. Inventario terminó en 38 unidades y registró dos entradas
  de 19.
- Se creó el cliente ficticio `QA-C-20261123` y el vendedor temporal “Vendedor
  QA temporal”; se creó `qa.worker` y se le asignó ese vendedor. El trabajador
  pudo iniciar sesión y consultar Compras, pero los controles para registrar
  proveedores, emitir pedidos y confirmar recepciones aparecieron deshabilitados.
  El menú del trabajador no mostró administración de usuarios, vendedores ni
  caja.
- Desde la sesión del trabajador se registró una venta de una unidad de
  `COSTENITA_175`: total $ 2.500 COP, pago inicial $ 1.000 COP y saldo $ 1.500
  COP. El inventario pasó a 37 y apareció una salida de una unidad. La primera
  observación mostró el saldo agregado, pero no la fila dentro del espacio
  visible. La investigación y corrección se registran abajo: la fila sí estaba
  en el modelo; faltaba desplazamiento general en la pantalla.
- El cierre de sesión del administrador volvió al login; luego el trabajador
  inició sesión y también volvió al login al cerrar sesión. Se cerró la
  aplicación y se descartaron los datos de prueba en memoria.

Este recorrido verificó visualmente login, navegación, administración de
usuarios, alta de cliente/vendedor/proveedor, pedido, recepción parcial y
completa, movimientos/stock y registro de venta en modo memoria. No valida
persistencia ni transacciones PostgreSQL desde la interfaz.

## Corrección de cartera y recorrido financiero PostgreSQL (2026-11-23)

### Diagnóstico y corrección de visibilidad

Se reprodujo el caso de una venta parcial de COP 2.500 con pago inicial de COP
1.000 y saldo de COP 1.500. `PortfolioController.refreshSelectedCustomer()`
recibía el cliente seleccionado, `salesWorkflow.listAll()` incluía la venta y
`salesTable` contenía la fila filtrada por cliente con saldo positivo. No faltaba
una fila en el modelo ni en PostgreSQL. La causa era de presentación: el FXML
tenía varias tablas en un `VBox` sin desplazamiento exterior, por lo que la tabla
de ventas pendientes podía quedar fuera de los límites de la ventana.

La raíz de `portfolio_view.fxml` ahora es un `ScrollPane` vertical con ancho
ajustado al contenido. Se conservan el controlador, los `fx:id` y los eventos;
la tabla de ventas tiene altura preferida para que el desplazamiento exterior
sea útil en ventanas pequeñas. No cambió la selección del cliente ni el contrato
de pagos.

La regresión JavaFX
`selectedCustomerPartialSaleIsVisibleAndPaymentRetryAfterLostResponseIsIdempotent`
comprueba que solo aparezca la venta del cliente elegido, que la fila y el saldo
de COP 1.500 sean seleccionables/visibles y que el formulario permita registrar
un abono de COP 500. La escena se mide y la fila se renderiza visible en
1280×800, 1024×768 y 800×600. La prueba ejecuta un flujo de memoria con trabajador
autorizado; simula que se pierde la respuesta después de confirmar y repite la
misma clave. Resultado: dos intentos, una sola escritura de COP 500, saldo de
ventas de COP 1.000 y sin cambios en la otra cuenta ni en stock/movimientos. Esta
es evidencia simulada de idempotencia de interfaz, no un reintento visual sobre
PostgreSQL.

### Recorrido manual JavaFX conectado al laboratorio aislado

Se preparó un clúster temporal propio, PostgreSQL 18.6, enlazado solo a
`127.0.0.1:49676`, con base `la_exclusiva_test`. No se utilizó el servicio
PostgreSQL local de 5432 ni bases compartidas o productivas. En ese clúster se
verificaron las tablas/columnas de V001, V002, V004, V100–V106 y se aplicaron
esas migraciones únicamente allí. La aplicación se inició con Java 17 en modo
`postgres` y su `LAEXCLUSIVA_DB_URL` apuntó exclusivamente a esa base. Se usaron
registros ficticios y una cuenta temporal; no se guardaron contraseñas en el
repositorio ni en esta bitácora.

Registros identificables creados para el recorrido: cliente
`QA-PR26-C-001`, vendedor `Vendedor QA PR26` (ID `SEL-FD621D47`), producto
`QA-PR26-BEER` y venta
`SALE-EEC5C53E-C741-4D8A-B3C3-8332FBBF3D4B`. La cuenta temporal fue
`qa-pr26-admin`. No se registraron teléfonos ni contraseñas en la documentación.

En la interfaz se confirmó una entrada ficticia de 2 unidades, la venta por COP
2.500, pago inicial de COP 1.000 y saldo de venta COP 1.500. Cartera mostró la
fila seleccionable del cliente correcto; un abono de COP 500 dejó saldo de COP
1.000. La deuda general ficticia de COP 300 se redujo con un abono de COP 100,
quedando COP 200, sin afectar el saldo de venta. Caja distinguió facturación de
COP 2.500 y cobros de ventas de COP 1.500, más COP 100 de pago de deuda general.
Un egreso manual de COP 25 dejó egresos en COP 25 y efectivo neto en COP 1.575.
La UI rechazó un egreso de COP 0 y un abono general de COP 201 cuando quedaban
COP 200; las cifras y el historial no cambiaron por esos rechazos.

La aplicación se cerró y se volvió a iniciar. El segundo login de la cuenta
temporal no se completó: la interfaz respondió “Credenciales inválidas o cuenta
inactiva”. La consulta de solo lectura posterior confirmó que la cuenta seguía
activa y que los datos comerciales persistían: una venta, dos pagos de venta
(COP 1.000 y COP 500), saldo agregado de cliente COP 1.200 (COP 1.000 de ventas
y COP 200 de deuda general), existencias 1, entrada 2/salida 1, deuda general
COP 300 menos COP 100 y los cuatro movimientos esperados de caja. `App` solo
crea la cuenta inicial cuando el repositorio de identidad está vacío; no la
reinicializa al arrancar con cuentas existentes. No se restableció ni cambió la
contraseña para forzar el login. Por ello la persistencia de los registros está
comprobada, pero el login UI tras reiniciar queda **no verificado**.

### Validación automatizada final

Con Microsoft JDK 17.0.18 y Maven 3.10.0, `mvn clean verify` terminó en
`BUILD SUCCESS`: **822 pruebas contabilizadas, 793 aprobadas, 0 fallidas, 0
errores y 29 omitidas**. Surefire confirma que 30 pruebas PostgreSQL reales se
ejecutaron y aprobaron; 29 integraciones optativas de identidad/concurrencia y
esquema incompleto se omitieron y no se cuentan como aprobadas. Las 30 incluyen
cartera (1), clientes/vendedores (1), finanzas (7), inventario (3), ventas JDBC
(5), productos (3), compras (6), ventas (1), identidad de solicitud (1) y
movimientos de stock (2). Las suites dirigidas de controladores financieros,
regresión JavaFX y autenticación aprobaron **63/63**. `git diff --check` pasó.
Maven conserva advertencias no bloqueantes del modelo efectivo de JavaFX y de
carga JavaFX desde módulo sin nombre.

### Comprobaciones que siguen pendientes

1. Repetir el login después del reinicio con una credencial temporal conocida,
   sin restablecer ni cambiar cuentas que ya existan.
2. Verificar en la interfaz los permisos y asignación de vendedor para una
   sesión trabajadora en modo PostgreSQL; la regresión automatizada de cartera
   usa una sesión trabajadora en memoria.
3. Repetir las comprobaciones visuales sobre el diseño que Daniel incorpore
   desde el PR #25, preservando el punto de navegación funcional.

## Límites y preparación de entrega

El módulo de compras no registra pagos a proveedores ni movimientos de caja:
una recepción representa mercancía recibida, no un egreso de efectivo. La fila
pendiente de cartera ya se muestra y permite registrar abonos; el reintento con
respuesta perdida está cubierto en una regresión JavaFX simulada. Los flujos de
cartera, caja y rechazo de valores inválidos también se recorrieron con JavaFX
conectado al PostgreSQL efímero; el segundo login después del reinicio queda
pendiente según se detalla arriba. La modernización visual de Daniel y el ícono
oficial siguen pendientes. Por ello no se prepara todavía un instalador de
Windows.
