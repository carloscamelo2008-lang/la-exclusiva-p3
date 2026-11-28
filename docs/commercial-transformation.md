# Transformación comercial: estado y decisiones pendientes

**Corte de trabajo:** 28 de noviembre de 2026. Esta rama local no se ha publicado ni fusionado.

## Cambios implementados en esta rama

- La composición de operación selecciona PostgreSQL por defecto y falla cerrada si faltan conexión, credenciales o esquema. El modo memoria exige selección explícita.
- La carga amplia de datos ficticios se conserva como fixture de pruebas. El modo comercial no la ejecuta. El argumento `--demo` se limita a cuentas temporales y requiere memoria explícita y contraseñas configuradas por el operador.
- Los clientes nuevos reciben un ID estable `CLI-<UUID>`. El formulario deja de pedir ID y cupo. V113 renombra el cupo histórico a `legacy_credit_limit` y conserva sus valores; las ventas nuevas no usan ese límite y el fiado permanece asociado a cada venta.
- La interfaz de recordatorios ya no presenta acciones de correo. La composición comercial rechaza `LAEXCLUSIVA_SMTP_ENABLED=true`; el seguimiento interno y el historial auditable permanecen.
- Los estados de cuenta PDF se guardan en una carpeta local configurable por `LAEXCLUSIVA_REPORTS_DIR`, con nombre único; la interfaz muestra la ruta y ofrece abrir la carpeta. El archivo es un informe de cartera, no facturación electrónica DIAN.
- El catálogo permite elegir una fotografía PNG/JPEG, valida tipo, decodificación, tamaño y dimensiones, la guarda por SHA-256 fuera de PostgreSQL y persiste solo una referencia. V114 añade la columna opcional con restricción SQL. La vista previa está disponible en inventario y ventas y muestra un estado alternativo cuando falta el archivo.
- El grupo de envase retornable permite escoger una opción existente o escribir una nueva.

La aplicación no carga ni importa datos reales del negocio. En la verificación inicial registrada debajo, V113 y V114 solo estaban en el repositorio y no se aplicaron a ninguna base; la validación complementaria posterior, fechada 2026-11-28, describe por separado su ejecución dentro de un clúster temporal aislado.

## Alcance aún pendiente antes de afirmar que el sistema es comercialmente completo

1. **Identificación del cliente:** Carlos debe definir tipo de documento, obligatoriedad, normalización, longitudes y regla de unicidad. No se inventó una política ni se agregó un campo con validación supuesta.
2. **Trabajador como responsable de venta:** la pantalla conserva el contrato actual de vendedor y el registro histórico `sale.seller_id`. La asignación de cuenta a vendedor funciona para trabajadores; falta una migración y contrato revisados que identifiquen al usuario autenticado en cada venta y permitan que el administrador venda con su propia identidad sin fabricar un vendedor ni alterar referencias históricas.
3. **Obligaciones de deuda general:** la cartera actual conserva el libro agregado con categoría y no permite seleccionar una obligación específica al abonar. Se requiere un modelo de obligación/asignación y una estrategia explícita para saldos históricos cuyos pagos no pueden atribuirse con certeza. La categoría todavía aparece en el flujo existente.
4. **Cuentas por pagar a proveedores:** compras y recepción distinguen pedido de mercancía recibida, pero no hay todavía libro de obligaciones, pagos parciales, vencimientos ni responsables del pago. No se presuponen plazos, tasas o acuerdos.
5. **Costos y ganancias:** no se presenta utilidad como resultado comercial hasta disponer de costos de compra confiables y su regla histórica por producto/lote. Ventas y efectivo son métricas distintas.
6. **Fotografías reales:** las seis fotografías originales suministradas por Carlos se inspeccionaron y se copiaron sin transformación a `docs/assets/catalogo-productos/`, con manifiesto, dimensiones y SHA-256. Son recursos de entrega para Daniel; no están en `src/main/resources`, no se incorporan automáticamente al runtime y todavía no se guardaron referencias en productos ni en una base. El contrato existente permite que una persona autorizada importe una foto mediante `ProductImageStore`, guarde la referencia por el servicio del catálogo y la resuelva desde `%USERPROFILE%\La Exclusiva\Productos` (o `LAEXCLUSIVA_PRODUCT_IMAGES_DIR`); respaldar esa carpeta junto con PostgreSQL. El manifiesto registra que el producto visual “Águila Original 250 ml” corresponde por volumen al ID actual `AGUILITA_250`, cuyo nombre en `InitialCatalog` es “Aguilita”; Carlos debe confirmar esa equivalencia antes de asignar la imagen.
7. **Datos auténticos, configuración y despliegue:** faltan el catálogo validado, cantidades/precios, clientes, proveedores, usuarios/vendedores y la configuración de la base del negocio. No se conectó a una base de negocio ni se preparó un instalador. El procedimiento de respaldo/restauración se documentó en `database/README.md`, pero no se ejecutó ni ensayó.

## Validación inicial de la transformación (Registro 45)

- Microsoft JDK 17.0.18 y Maven 3.10.0.
- La batería dirigida de almacén de imágenes, controladores JavaFX/FXML, plan de laboratorio y repositorio JDBC terminó `BUILD SUCCESS`: **33 contabilizadas, 30 aprobadas y 3 integraciones PostgreSQL omitidas**, con 0 fallos y 0 errores. Las 3 omisiones corresponden a `ProductRepositoryJdbcPostgresIntegrationTest`, porque no se configuró `LAEXCLUSIVA_TEST_DB_URL`. La ejecución final completa incluye además la regresión de vista previa en ventas.
- La última `mvn clean verify` con Microsoft JDK 17.0.18 y Maven 3.10.0 terminó `BUILD SUCCESS`: **918 contabilizadas, 851 aprobadas, 0 fallidas, 0 errores y 67 omitidas**. Las 67 corresponden a integraciones PostgreSQL optativas sin URL/propiedades de laboratorio. Se generó el JAR.
- La primera verificación completa detectó un test visual antiguo que buscaba el control `emailField`, retirado junto con la UI de correo. Se actualizó la lista del test a los controles internos actuales; `CollectionDashboardVisualTest` pasó 4/4 en la corrida final.
- Una verificación posterior llegó a generar los 918 reportes sin fallos, pero Surefire terminó con un error no reproducible al iniciar un fork; no hubo dump ni reporte de error individual. La repetición con `mvn -e clean verify` terminó correctamente y reportó los mismos totales.
- Docker no está instalado y no hay digest/secret del laboratorio configurados. No se ejecutó PostgreSQL real, no se aplicaron V113/V114 y no se modificó ninguna base local, compartida o productiva.
- `py -3` informó que no hay Python instalado; por eso tampoco se ejecutó el lanzador `actions-lab.py`. La secuencia y restricciones SQL sí tienen pruebas estáticas Java, pero esto no sustituye el laboratorio PostgreSQL.
- JavaFX/FXML se ejercitó mediante pruebas automatizadas, incluidas carga de imagen en inventario/ventas, nuevo grupo retornable y layouts de alerta/cartera; no se realizó un recorrido manual visual de escritorio en esta intervención.

## Configuración local

Para operación persistente, define `LAEXCLUSIVA_PERSISTENCE_MODE=postgres`, `LAEXCLUSIVA_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` en el proceso de la aplicación. Verifica y aplica manualmente las migraciones requeridas en una ventana controlada; el arranque no las ejecuta.

En una base vacía, el primer administrador usa `LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (por defecto `admin`) y `LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`, que debe cumplir la política existente. El bootstrap no recrea ni reemplaza cuentas. Mantén la contraseña fuera del repositorio, la línea de comandos compartida y la bitácora.

Configura `LAEXCLUSIVA_PRODUCT_IMAGES_DIR` y `LAEXCLUSIVA_REPORTS_DIR` a carpetas incluidas en el procedimiento de respaldo del equipo. Si no se definen, se usan subcarpetas persistentes bajo el perfil del usuario.

## Revisión humana requerida

- Carlos: definir política de documento de identidad, confirmar la asociación “Águila Original” / `AGUILITA_250`, aportar datos comerciales validados y revisar/ensayar el procedimiento de respaldo y restauración en una base aislada.
- Jesús: revisar el contrato/migraciones propuestos para identidad de actor de venta, obligaciones generales individualizadas y cuentas por pagar antes de habilitar esas escrituras.
- Daniel: revisar el manifiesto de `docs/assets/catalogo-productos/` e integrar su visualización conservando los controles y contratos actuales de Inventario y Ventas; el staging no cambia FXML ni comportamiento JavaFX.
- Equipo: revisar V113/V114 y ejecutar sus pruebas en un laboratorio PostgreSQL efímero antes de aplicar las migraciones en una base de negocio.

## Actualización de coordinación y validación (2026-11-28)

### Recursos visuales preparados

Se copiaron seis imágenes JPEG originales a `docs/assets/catalogo-productos/`. Las copias conservan el SHA-256 de origen y el manifiesto contiene el ID existente propuesto para cada producto. No se modificaron los originales, no se recortaron ni comprimieron imágenes, no se inventaron precios o cantidades y no se tocaron el catálogo ni la interfaz. La carpeta contiene material de coordinación documental, no recursos runtime. La imagen `costena_bacana_330ml.jpeg` conserva su extensión original; al importarla, el almacén existente normaliza el nombre derivado del hash a `.jpg`.

La carpeta de origen también incluía un ZIP con esos seis nombres; se inspeccionaron sus entradas y se usaron las seis imágenes sueltas, sin duplicar el archivo ZIP en el repositorio.

### Validación PostgreSQL aislada

- Se utilizó un clúster PostgreSQL 18.6 temporal, enlazado solo a `127.0.0.1:55432`, con la base `la_exclusiva_test` y el usuario técnico del laboratorio. El clúster quedó aislado bajo `%LOCALAPPDATA%\Temp`; el servicio local del puerto 5432 y cualquier base existente, incluida `la_exclusiva`, no se usaron ni modificaron.
- El script `database/validation/identity/application.psql` aplicó V001, V004, V002 y V100–V114 únicamente al clúster temporal. Sus verificaciones de conservación del cupo histórico V113 y de restricción de referencias de imágenes V114 finalizaron correctamente. Las pruebas automatizadas usaron fixtures de laboratorio; no se cargaron datos reales.
- Con Microsoft JDK 17.0.18 y Maven 3.10.0, `mvn -e clean verify` terminó en **BUILD FAILURE**: **918 pruebas, 873 aprobadas, 3 fallidas, 10 con error y 32 omitidas**. Surefire muestra 36 pruebas de integración PostgreSQL ejecutadas: 23 aprobadas, 3 fallidas y 10 con error. Las 32 omitidas son integraciones optativas que requieren autorización/configuración específica; no se cuentan como aprobadas. No se generó un artefacto nuevo porque la fase de pruebas detuvo la verificación.
- Las suites automatizadas `ProductImageStoreTest` (2/2) e `InventoryControllerReviewRegressionTest` (5/5) pasaron. Esto no acredita una revisión visual manual ni que estas seis fotos ya se vean en la aplicación.

### Hallazgos del primer `clean verify` (antes de corregir)

1. **Regresión de lectura JDBC:** `ProductRepositoryJdbc.readProduct(ResultSet)` lee `image_filename`, pero `InventoryRepositoryJdbc.INVENTORY_SELECT` no proyecta esa columna. PostgreSQL reportó `The column name image_filename was not found in this ResultSet`. Esto causa errores en lecturas de inventario y movimientos y bloquea escenarios de operaciones financieras/ventas que consultan existencias. La corrección técnica probable es incluir `p.image_filename` en esa proyección y añadir una regresión PostgreSQL para dicho lector. No se modificó el DAO compartido porque su revisión debe coordinarse con Jesús; el defecto debe resolverse antes de afirmar que inventario/ventas PostgreSQL están validados.
2. **Aserción de escala monetaria:** `CustomerCrudPostgresIntegrationTest.editsAndDeletesWithPhoneAndReferenceConstraints` compara `BigDecimal.ZERO` con el valor SQL `0.00` mediante igualdad sensible a escala. Los valores numéricos son equivalentes; la regresión observada está en la aserción del test. El ajuste propuesto es comparar numéricamente (`compareTo`) o usar una expectativa de escala compatible. No se alteró el contrato financiero.
3. Los fallos y errores restantes de las suites PostgreSQL son consecuencias en cascada de la columna ausente: inventario, movimientos, rollback financiero y flujos de venta JDBC. No se atribuyen a fallos independientes de consistencia transaccional.

### Impacto de los siete commits locales

Los siete commits originales permanecen intactos en `feature/commercial-transformation`. La tabla identifica las áreas que necesitan revisión cruzada antes de integrar:

| Commit | Área modificada | Revisión de coordinación |
|---|---|---|
| `8f19bfb` | Arranque, `App`, `ApplicationServices` y composición de persistencia | Revisar con quien mantenga la composición JavaFX; afecta el límite entre arranque, autenticación y persistencia. |
| `6d4672a` | Retiro de datos demo del arranque productivo | Coordinar si se cambia el flujo de inicio/demo; no cambia la carga de negocio. |
| `a3cc8a4` | Cliente, ventas y V113 para retirar el cupo nuevo | Revisión de Jesús requerida por el contrato financiero y la migración histórica. |
| `4da86e8` | Cartera, controlador/FXML, PDF local y correo desactivado | Revisión de Daniel por FXML/controlador; conservar el contrato visual y eventos existentes. |
| `28ea81c` | Referencias de imagen, repositorios, inventario/ventas, FXML y V114 | Revisión de Daniel por visualización; revisión de Jesús por persistencia/migración y consultas JDBC. El error de `image_filename` quedó expuesto por integración. |
| `f41b237` | Documentación y scripts del laboratorio | Coordinación; comprobar scripts y documentación con el responsable del laboratorio. |
| `04fd4c6` | Bitácora y documentación del estado | Solo documentación; conservar el registro histórico. |

No se ejecutó un recorrido manual de escritorio. El inicio visual en JavaFX, la asignación real de las imágenes, la comprobación de permisos desde una sesión abierta y la actualización visual posterior a una carga quedan pendientes; las pruebas FXML no sustituyen esas comprobaciones.

## Cierre de regresiones PostgreSQL (2026-11-28)

### Causas raíz y correcciones

El análisis de los reportes Surefire de la primera corrida encontró 13 resultados adversos:

1. **Diez resultados** (errores de lectura y una aserción de tipo de excepción derivada) venían de `InventoryRepositoryJdbc.INVENTORY_SELECT`, que no devolvía `p.image_filename` aunque el mapper compartido `ProductRepositoryJdbc.readProduct` lo exige. Eso afectó lecturas de inventario y, en cascada, rollback financiero y ventas JDBC.
2. **Dos resultados** (un error de lectura y una aserción de tipo de excepción derivada) tenían la misma causa raíz en `StockMovementRepositoryJdbc.MOVEMENT_SELECT`, otra consulta que reconstruía el producto con el mapper compartido sin proyectar `image_filename`.
3. **Un fallo de aserción** comparaba `BigDecimal.ZERO` con `0.00` mediante igualdad sensible a escala. No indicaba diferencia de valor monetario.

Se aplicó la corrección mínima en ambas consultas, sin cambiar el mapper, la semántica del inventario ni la lógica financiera. Se añadió una regresión PostgreSQL para `findByProductId`, `findAll` y `findByProductIdForUpdate` que conserva la referencia de imagen; el historial de movimientos ahora comprueba también la referencia reconstruida. En `CustomerCrudPostgresIntegrationTest` el saldo y el cupo legado se comparan numéricamente con `compareTo`.

La ampliación de las integraciones optativas reveló dos defectos exclusivos de fixtures de prueba: la limpieza JavaFX intentaba borrar las cuentas QA antes de quitar sus eventos de auditoría con FK `RESTRICT`, y el teléfono ficticio contenía letras hexadecimales. La limpieza ahora elimina eventos únicamente de esos usuarios QA antes de borrar las cuentas, y el fixture genera un teléfono solo con dígitos. No se modificó lógica productiva JavaFX ni diseño/FXML.

### Verificación final

- Se creó un clúster PostgreSQL 18.6 nuevo y temporal en `127.0.0.1:55432`, con `la_exclusiva_test`, propietario de laboratorio y nombre de clúster verificado. `application.psql` aplicó V001, V004, V002 y V100–V114 en ese clúster; las validaciones V113/V114 pasaron. No se usó el servicio existente en 5432 ni se conectó a `la_exclusiva` o a una base comercial.
- Con Microsoft JDK 17.0.18 y Maven 3.10.0, `mvn -e clean verify` terminó **BUILD SUCCESS**: **919 pruebas, 898 aprobadas, 0 fallidas, 0 errores y 21 omitidas**. Se generó `target/la-exclusiva-p3-1.0-SNAPSHOT.jar`.
- Surefire registra 68 casos de prueba en suites PostgreSQL: **47 se ejecutaron y aprobaron; 21 se omitieron**. Las omisiones pertenecen a `AccountInstance` (3), `ActiveSellerConcurrency` (6) y `UserSecurityVersion` (11), cuyas barreras exigen el puerto 5432 ocupado por el servicio local, y `IncompleteIdentitySchema` (1), que requiere una base separada con esquema deliberadamente incompleto. No se contaron como aprobadas.
- La batería dirigida de los siete escenarios de integración relevantes (`CustomerCrud`, finanzas, composición `IdentityApplication`, inventario, ventas JDBC, movimientos e identidad JavaFX) terminó **25/25**, sin fallos, errores ni omisiones.
- Una consulta final de solo lectura confirmó que `user_account`, `administrative_audit_event`, `customer`, `seller`, `beer_product`, `supplier`, `sale` y `stock_movement` quedaron en cero en el laboratorio. No se limpió ninguna tabla en otra base. El clúster efímero se detuvo al terminar; sus archivos temporales permanecen en `%LOCALAPPDATA%\Temp`.
- Persisten advertencias no bloqueantes del modelo efectivo OpenJFX y de carga JavaFX desde un módulo sin nombre. Los stack traces de pruebas que simulan fallos de interfaz son esperados; Surefire las contabilizó como aprobadas.

### Commits correctivos

- `6aa0512` — añade la referencia de imagen a la proyección JDBC de inventario y cubre las tres lecturas.
- `f733cbb` — añade la referencia de imagen al historial JDBC de movimientos y a su regresión PostgreSQL.
- `2515b4f` — compara importes monetarios por valor numérico en la prueba CRUD de clientes.
- `544a5ba` — corrige el fixture y el orden de limpieza acotada de la integración JavaFX/PostgreSQL.
- La actualización de este informe y de la bitácora va en un commit documental separado.

Los nueve commits que existían antes de las correcciones siguen conservados. Las consultas JDBC corregidas deben recibir revisión de Jesús por su contrato de datos compartido. Daniel conserva pendiente integrar visualmente las seis fotos; no se tocó su diseño ni se asignaron imágenes a productos en la base.
