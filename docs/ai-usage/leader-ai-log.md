# Bitácora de uso de IA — Líder técnico

**Integrante:** Carlos Eduardo Camelo Montaño  
**Rol:** Líder técnico e integración  
**Proyecto:** La Exclusiva  
**Estado:** Registro en desarrollo

## Registro 1

- **Fecha:** 2026-10-25.
- **Herramienta:** ChatGPT.
- **Fase / tarea:** configuración inicial del proyecto; rama feature/project-setup.
- **Objetivo:** organizar el repositorio, mejorar el .gitignore, establecer convenciones y preparar la documentación inicial para revisión del equipo.
- **Resumen de la ayuda recibida:** propuesta de configuración Java/Maven compatible con IntelliJ IDEA y NetBeans; estructura inicial por capas; borradores de README, TEAM, análisis del negocio y requerimientos; recomendaciones para separar main, develop y ramas de trabajo.
- **Qué se utilizó:** se revisaron y adaptaron las propuestas a la información real compartida por el negocio, la guía del docente y las reglas del equipo. La configuración y los documentos se dejaron en una rama separada para solicitar revisión.
- **Qué debe verificarse:** compilación Maven en un entorno local, exactitud de las reglas de inventario y precios, revisión de los documentos por Jesús y Daniel, y consistencia del diagrama con el código final.
- **Resultado:** configuración y documentación inicial preparadas para revisión; no se considera aprobada ni fusionada hasta completar el proceso de Pull Request.

## Registro 2

- **Fecha:** 2026-10-29.
- **Herramienta:** Codex integrado en IntelliJ IDEA.
- **Fase / tarea:** documentación Java de integración y ventas; rama `docs/javadocs-console-sales`, creada desde `origin/develop`.
- **Objetivo:** explicar en español las responsabilidades, reglas, parámetros, retornos y excepciones de los componentes de consola y ventas sin alterar su comportamiento.
- **Resumen de la ayuda recibida:** revisión de los contratos de ventas, presentaciones, persistencia de ventas y navegación de consola para mejorar Javadocs y comentarios donde aclaran validaciones, pagos, unidades o compensaciones.
- **Archivos Java documentados:** `Main.java`, `ConsoleMenu.java`, `Sale.java`, `SaleLine.java`, `SalePresentation.java`, `UnitPresentation.java`, `HalfBasketPresentation.java`, `FullBasketPresentation.java`, `SaleService.java`, `SaleRepository.java` e `InMemorySaleRepository.java`.
- **Qué se utilizó:** se ampliaron los Javadocs de clases, constructores y operaciones públicas; se aclararon el precio histórico de una línea, la conversión de presentaciones a unidades, los requisitos de pago y cliente, el orden de coordinación de ventas y el uso de copias en el repositorio en memoria. Se ajustó la descripción de los pagos para indicar que devuelve una copia, no una vista.
- **Cambios efectuados:** solo Javadocs y comentarios internos en los once archivos anteriores. No se cambiaron firmas, reglas de negocio ni lógica ejecutable. Commit Java creado: `2c5676b` (`docs: improve JavaDoc for sales and console modules`).
- **Verificación:** Maven 3.9.16 con JDK 17.0.18 ejecutó `clean verify`: `BUILD SUCCESS`, 100 pruebas ejecutadas, 0 fallos, 0 errores y 0 omitidas. `git diff --check` pasó antes del commit Java.
- **Limitaciones:** `mvn` no está en el `PATH` y no existe `mvnw.cmd`; se usó Maven incluido en IntelliJ. El entorno normal bloqueó la descarga de `maven-clean-plugin:3.2.0`, así que la verificación completa requirió permitir a Maven acceder a Maven Central. No se instalaron herramientas ni se cambiaron variables globales; Maven descargó el plugin faltante a su caché local.

## Registro 3

- **Fecha:** 2026-10-29.
- **Herramienta:** Codex integrado en IntelliJ IDEA.
- **Fase / tarea:** auditoría y corrección final de Javadocs de ventas; rama `docs/javadocs-console-sales`.
- **Hallazgos:** el Javadoc de `SaleService.registerSale` podía sugerir un orden distinto al real; `SaleLine` se describía como inmutable aunque conserva y expone la referencia a `SalePresentation`; algunas descripciones de servicio prometían copias que no exige el contrato `SaleRepository`.
- **Cambios efectuados:** se documentó el orden inventario, deuda condicional y almacenamiento de la venta, junto con las compensaciones respaldadas por el código; se describieron las referencias y copias de `SaleLine` sin prometer independencia total; se limitaron los retornos de `SaleService` y `SaleRepository` al contrato, dejando las copias defensivas documentadas en `InMemorySaleRepository`. No se modificó lógica ejecutable. Commit Java: `2aa67ab` (`docs: correct sales JavaDoc contracts`).
- **Verificación:** Maven 3.9.16 con JDK 17.0.18 ejecutó `clean verify`: `BUILD SUCCESS`, 100 pruebas ejecutadas, 0 fallos, 0 errores y 0 omitidas. `mvn javadoc:javadoc` terminó en `BUILD SUCCESS` y generó `target/reports/apidocs/index.html`; DocLint informó 100 advertencias de comentarios o etiquetas faltantes en otros fuentes y 0 errores, sin advertencias en los tres archivos corregidos. Se comprobó el HTML de las tres clases y `git diff --check` pasó antes de registrar esta entrada. Maven se ejecutó con variables de proceso, sin cambiar la configuración global.

## Registro 4

- **Fecha:** 2026-10-29.
- **Herramientas:** Codex en la aplicación de escritorio, PowerShell, Git y Maven 3.9.16 con JDK 17.0.18.
- **Fase / tarea:** integración de autenticación, autorización por rol, inicialización demo y protección del menú de consola; rama `feature/auth-demo-integration`.
- **Objetivo:** conectar el inicio de sesión con el menú, aplicar los permisos de administrador y trabajador, y asegurar que las ventas del trabajador usen el vendedor de su cuenta.
- **Cambios aceptados:** `Main` construye la autenticación y autorización, solicita credenciales y conecta la sesión al menú. En el arranque normal solicita crear la cuenta inicial de administrador cuando no hay usuarios. El modo `--demo` carga datos ficticios y crea las cuentas `admin.demo` y `trabajador.demo`, con contraseñas leídas de `LAEXCLUSIVA_DEMO_ADMIN_PASSWORD` y `LAEXCLUSIVA_DEMO_WORKER_PASSWORD`. `ConsoleMenu` filtra opciones y comprueba permisos en sus manejadores; requiere una sesión activa y no permite al trabajador escoger vendedor para una venta. Se agregaron permisos para consultar y registrar vendedores. Los datos y las cuentas están en repositorios en memoria.
- **Pruebas y verificación reportada:** `mvn clean verify` usando JDK 17 terminó con `BUILD SUCCESS`: 121 pruebas ejecutadas, 0 fallos, 0 errores y 0 omitidas. Las pruebas actualizadas cubren permisos de ambos roles, accesos denegados, rechazo de sesiones nulas o inactivas y asociación de ventas al vendedor de la cuenta autenticada.
- **Commits funcionales:** `ad79b33` (`Enforce role permissions in console menu`) y `65c7ffe` (`Test menu authorization and worker sales`).
- **Alcance del cierre:** se conservaron los cuatro commits documentales anteriores de la rama. Esta entrada registra el trabajo y la verificación reportada; no cambia el código funcional.

## Registro 5

- **Fecha:** 2026-10-30.
- **Herramienta:** Codex en la aplicación de escritorio.
- **Fase / tarea:** corrección documental final previa a publicar la rama `feature/auth-demo-integration`.
- **Cambio efectuado:** se separó la etiqueta `@param args` del texto descriptivo en el Javadoc de `Main.main`. No se modificó funcionalidad ni pruebas.
- **Verificación:** `git diff --check` pasó. `mvn javadoc:javadoc` terminó en `BUILD SUCCESS`; DocLint informó 100 advertencias en otros fuentes y 0 errores. No hubo advertencias en el Javadoc corregido de `Main.main`.
- **Commit:** `docs: fix Main entry point JavaDoc`.

## Registros siguientes

Añadir una sección por cada interacción relevante de IA vinculada con tareas posteriores. Describir únicamente lo que realmente se consultó, revisó y aplicó.

## Registro 6

- **Fecha:** 2026-11-12.
- **Herramienta:** Codex en la aplicación de escritorio.
- **Fase / tarea:** persistencia JDBC de ventas; rama `feature/sales-jdbc`, creada desde `develop` sincronizada con `origin/develop` en `56169745`.
- **Objetivo:** iniciar el adaptador JDBC de ventas con trazabilidad por microcommits, reconstitución histórica segura y pruebas.
- **Resumen fiel del encargo:** implementar `SaleRepositoryJdbc` con escrituras atómicas, recuperación completa, pagos/abonos sin duplicaciones, borrado no destructivo, pruebas unitarias y de integración opcionales, documentación del futuro límite transaccional de ventas/inventario/cartera y bitácora IA; no modificar JavaFX ni los DAO de inventario/productos de Jesús, ni hacer push/merge.
- **Trabajo de Codex en este bloque:** inspeccionó `TEAM.md`, la bitácora existente, contratos JDBC, esquema V001, entidades, repositorios, servicios y pruebas. Identificó que el modelo no tenía una fábrica para conservar el ID de venta ni un accessor del snapshot de capacidad. Añadió `Sale.reconstitute(...)` y `SaleLine.reconstitute(...)` con validaciones, conservación de ID, fecha, precio y capacidad histórica, y tests unitarios para esos contratos.
- **Archivos creados o modificados:** `src/main/java/com/laexclusiva/model/Sale.java`, `src/main/java/com/laexclusiva/model/SaleLine.java`, `src/test/java/com/laexclusiva/model/SaleTest.java` y `database/sales-jdbc-integration.md`.
- **Decisiones técnicas asistidas por Codex:** mantener separado el constructor de ventas nuevas y la fábrica de reconstitución; no derivar precio histórico de catálogo; dejar explícito que la identidad de pago falta en el modelo y diseñar el update solo como anexado bajo bloqueo, sin borrar ni reescribir pagos. La propuesta de anexado sigue pendiente de validación humana.
- **Pruebas reales:** `mvn -Dtest=SaleTest test` con Java 17.0.21 y Maven 3.10.0: BUILD SUCCESS, 12 pruebas, 0 fallos, 0 errores y 0 omitidas. No se ejecutó PostgreSQL en este bloque.
- **Errores y resolución:** ninguno durante este bloque. Se observaron las advertencias preexistentes de los modelos Maven de JavaFX; no afectan la prueba de modelo.
- **Commit asociado:** `541e0ff` (`feat(sales-jdbc): support historical sale reconstitution`).
- **Pendiente de revisión humana:** Carlos debe revisar el contrato de anexado de pagos por `(importe, fecha)` y confirmar su adecuación antes de habilitar la integración de servicio. También quedan pendientes la implementación JDBC, su suite y la prueba PostgreSQL opcional.

## Registro 7

- **Fecha:** 2026-11-13.
- **Herramienta:** Codex en la aplicación de escritorio.
- **Fase / tarea:** adaptador JDBC de ventas y pruebas; rama `feature/sales-jdbc`.
- **Objetivo:** persistir ventas completas atómicamente, recuperar su historial y anexar abonos sin alterar pagos previos.
- **Resumen de la ayuda recibida:** se implementó `SaleRepositoryJdbc` con `JdbcConnectionProvider`, `JdbcTransactionManager`, sentencias parametrizadas y recursos JDBC cerrados. `save` inserta cabecera, líneas, snapshots y pagos en una transacción; consultas agrupan líneas/pagos; `update` bloquea la venta y solo añade nuevas ocurrencias; `deleteById` rechaza el borrado físico.
- **Archivos creados o modificados:** `src/main/java/com/laexclusiva/repository/jdbc/SaleRepositoryJdbc.java`, `src/test/java/com/laexclusiva/persistence/jdbc/SaleRepositoryJdbcTest.java`, `src/test/java/com/laexclusiva/persistence/jdbc/SaleRepositoryJdbcPostgresIntegrationTest.java`, `database/sales-jdbc-integration.md` y esta bitácora.
- **Decisiones técnicas asistidas por Codex:** `findAll()` recupera cabeceras, líneas y pagos con tres consultas; los snapshots no se reconstruyen desde precios/capacidades actuales. Como `SalePayment` carece de ID de dominio, el update serializa sobre la fila de venta y compara el multiconjunto de `(importe, occurred_at)` normalizado a dos decimales/microsegundos. La limitación y el diseño futuro de servicio quedaron documentados; requieren revisión de Carlos.
- **Pruebas reales:** `mvn -Dtest=SaleRepositoryJdbcTest,SaleRepositoryJdbcPostgresIntegrationTest,SaleTest test`, con Java 17.0.21 y Maven 3.10.0: BUILD SUCCESS, 27 pruebas contabilizadas, 26 aprobadas y 1 omitida; 0 fallos y 0 errores. La prueba omitida requiere `LAEXCLUSIVA_TEST_DB_URL`; no se conectó a PostgreSQL ni se cambió base alguna.
- **Errores encontrados y resolución:** (1) compilación porque `Optional.map` no acepta una carga que lanza `SQLException`; se cambió a una rama explícita dentro del callback JDBC. (2) compilación del fake JDBC porque una lambda `forEach` no propagaba `SQLException`; se reemplazó por un bucle. (3) dos aserciones comparaban `BigDecimal` ignorando la escala `NUMERIC(19,2)`; se ajustaron al valor con escala de base de datos. Las ejecuciones dirigidas finales pasaron.
- **Advertencias observadas:** Maven mostró advertencias de modelos efectivos de JavaFX que ya aparecían en las pruebas de modelo. No hubo warnings de compilación del código nuevo.
- **Commit asociado:** `509a611` (`feat(sales-jdbc): persist sales atomically and append payments`).
- **Pendientes de revisión humana:** validar la clave de equivalencia de abonos por importe/fecha; revisar el documento de diseño de unidad de trabajo y contratos con los DAO de Jesús; aplicar V001 manualmente en `la_exclusiva_test` y correr la prueba PostgreSQL cuando exista un entorno dedicado.

## Registro 8

- **Fecha:** 2026-11-13.
- **Herramienta:** Codex en la aplicación de escritorio.
- **Fase / tarea:** verificación de integración del trabajo local en `feature/sales-jdbc`.
- **Objetivo:** ejecutar la suite completa con Java 17 y registrar el estado real del adaptador antes de entregar la rama.
- **Resumen de la ayuda recibida:** se ejecutó `mvn clean verify` después de los dos microcommits de código. La compilación cubrió 57 fuentes principales y 18 de prueba. Maven terminó con `BUILD SUCCESS`; se contabilizaron 154 pruebas, 153 aprobadas, 0 fallidas, 0 errores y 1 omitida por no estar configurada `LAEXCLUSIVA_TEST_DB_URL`.
- **Archivos creados o modificados:** `database/sales-jdbc-integration.md` y esta bitácora para registrar el resultado final.
- **Decisiones técnicas asistidas por Codex:** mantener la prueba PostgreSQL como optativa y no conectarse a una base de desarrollo; describir la suite simulada como unitaria, no como integración real.
- **Errores y resolución:** no hubo fallos en la verificación completa. Las advertencias fueron 6 problemas al formar modelos efectivos de JavaFX por dependencia y el aviso de configuración JavaFX no modular durante `AuthenticationServiceTest`; no impidieron las pruebas.
- **Commit asociado:** `27d86b3` (`docs(sales-jdbc): record verification and service design`), commit documental final que registra la verificación de esa sesión.
- **Pendientes de validación humana:** revisar el adaptador y aprobar la semántica de idempotencia de pagos; verificar la prueba con PostgreSQL sobre `la_exclusiva_test` después de preparar esa base; acordar con los responsables de inventario/cartera la futura unidad transaccional antes de integrar `SaleService`.

## Registro 9

- **Fecha:** 2026-11-13.
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git y Maven 3.10.0 con JDK 17.0.21.
- **Fase / tarea:** identidad idempotente de abonos JDBC; rama `feature/sales-jdbc`.
- **Objetivo:** distinguir reintentos de pagos legítimos, proteger el saldo pendiente bajo concurrencia y conservar la historia de pagos.
- **Resumen fiel del encargo:** Carlos aprobó reemplazar la identificación por `(importe, occurred_at)` con una clave persistente por abono; solicitó una migración V002, un puerto explícito de anexado idempotente, rechazo de `update(Sale)` por instantánea, pruebas simuladas y PostgreSQL concurrentes, documentación y microcommits. Se indicó no integrar `SaleService`, no ejecutar migraciones, no modificar módulos de inventario/productos/JavaFX y no hacer push ni merge.
- **Diagnóstico que motivó el cambio:** las pruebas temporales de la revisión previa mostraron que dos solicitudes independientes con importe y fecha iguales podían confundirse como una sola. Una instantánea sin clave tampoco permite resolver una copia antigua contra pagos ya persistidos.
- **Decisión técnica aprobada por Carlos y aplicada por Codex:** `SalePaymentAppender.appendPayment(saleId, amount, idempotencyKey)` es el puerto estable. El adaptador bloquea la venta, consulta primero la clave, compara el importe, recalcula el saldo desde líneas y pagos persistidos e inserta una sola vez dentro de la transacción actual. Repetir clave e importe devuelve el pago y la fecha guardados; repetir clave con importe distinto se rechaza. V002 agrega clave nullable y unicidad `(sale_id, idempotency_key)`; los pagos históricos/iniciales pueden mantener `NULL`. `SaleRepositoryJdbc.update(Sale)` ahora rechaza explícitamente las instantáneas ambiguas.
- **Archivos creados o modificados:** `database/migrations/V002__payment_idempotency.sql`, `src/main/java/com/laexclusiva/repository/SalePaymentAppender.java`, `src/main/java/com/laexclusiva/repository/jdbc/SaleRepositoryJdbc.java`, `src/test/java/com/laexclusiva/persistence/jdbc/SaleRepositoryJdbcTest.java`, `src/test/java/com/laexclusiva/persistence/jdbc/SaleRepositoryJdbcPostgresIntegrationTest.java`, `database/README.md`, `database/jdbc-integration-contract.md`, `database/sales-jdbc-integration.md` y esta bitácora.
- **Pruebas ejecutadas y resultados reales:** con JDK 17.0.21 y Maven 3.10.0, `mvn -Dtest=SaleRepositoryJdbcTest,SaleRepositoryJdbcPostgresIntegrationTest test` terminó en `BUILD SUCCESS`: 18 pruebas contabilizadas, 17 aprobadas, 0 fallidas, 0 errores y 1 omitida. La prueba simulada verifica pagos iguales con claves distintas y fecha fija idéntica, reintento posterior que conserva fecha, clave/importe conflictivos, montos inválidos, saldo, snapshots, historial y rollback transaccional. La prueba PostgreSQL que verifica `23505` y carreras con dos conexiones está escrita, pero fue omitida porque `LAEXCLUSIVA_TEST_DB_URL` no estaba configurada. No se conectó ni se ejecutó migración alguna.
- **Verificación completa:** `mvn clean verify` con JDK 17.0.21 terminó en `BUILD SUCCESS`: 157 pruebas contabilizadas, 156 aprobadas, 0 fallidas, 0 errores y 1 omitida (PostgreSQL, variable de prueba no configurada). Maven informó 6 advertencias de modelos efectivos para cada dependencia JavaFX; JavaFX también emitió el aviso de configuración no modular durante `AuthenticationServiceTest`.
- **Errores encontrados y resolución:** una primera invocación de PowerShell interpretó la lista de clases de prueba como separadores; se entrecomilló el argumento `-Dtest` y la ejecución posterior terminó correctamente. No hubo errores de compilación ni fallos de pruebas simuladas.
- **Commits asociados:** `e5fbbe3` (`feat(sales-jdbc): add payment idempotency migration`) y `826f727` (`feat(sales-jdbc): append payments with idempotency keys`). Los cambios documentales y esta bitácora se registran en el commit documental de cierre de la intervención.
- **Limitaciones y pendientes humanos:** Carlos debe aplicar V002 solo después de verificar que la base destino sea `la_exclusiva_test`, configurar únicamente `LAEXCLUSIVA_TEST_DB_URL` y revisar/ejecutar la prueba PostgreSQL real de unicidad y concurrencia. Antes de integrar `SaleService`, el equipo debe definir quién genera y conserva la misma clave estable a través de reintentos; no se cambió ni conectó el servicio. Debe confirmarse que el entorno preparado ya aplica V002 antes de probar el adaptador.
- **Validación humana:** esta entrada describe trabajo asistido por Codex y resultados observados localmente; Carlos aún debe revisar el contrato, la migración y las garantías PostgreSQL reales.

## Registro 10

- **Fecha:** 2026-11-13.
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git y Maven 3.10.0 con JDK 17.0.21.
- **Fase / tarea:** cierre técnico y preparación para revisión del PR de `feature/sales-jdbc`; no se publicó ni abrió PR.
- **Objetivo:** confirmar la evidencia PostgreSQL reciente, completar escenarios reales faltantes, auditar el diff contra `develop` y preparar una descripción de PR.
- **Resumen fiel del encargo:** revisar Git y `target/surefire-reports`, distinguir JDBC simulado de PostgreSQL real, cubrir idempotencia, concurrencia, saldo, rollback e historial, auditar los contratos, documentar resultados y preparar el PR sin push ni merge.
- **Evidencia anterior inspeccionada:** los XML/TXT de Surefire tienen fecha 2026-11-13 11:57 hora local y suman 157 pruebas, 0 fallidas, 0 errores y 0 omitidas. El reporte `SaleRepositoryJdbcPostgresIntegrationTest` indica 1 aprobada y 0 omitidas; las otras 156 son unitarias/simuladas. El método solo continúa si `DatabaseConfig.fromTestEnvironment()` acepta exactamente `la_exclusiva_test`; ejecutó SQL de V002 y comprobó la restricción única, por lo que V001/V002 funcionaban en esa ejecución. No se imprimieron credenciales.
- **Cambio asistido por Codex:** se amplió `SaleRepositoryJdbcPostgresIntegrationTest` con pago inicial, dos claves distintas para el mismo importe/fecha, reintento usando un reloj posterior, conflicto de importe para una clave usada y rollback luego de insertar cabecera/primera línea y fallar una FK. La limpieza se limita a IDs aleatorios de la ejecución. Las comprobaciones existentes de unicidad PostgreSQL, reintentos, dos conexiones concurrentes y saldo se conservaron.
- **Auditoría del código:** el diff frente a `origin/develop` se limita a modelos de venta históricos, persistencia JDBC de ventas, pruebas y documentación. `SaleService` no se conecta al adaptador; `Main` continúa componiendo repositorios en memoria. No se identificó un defecto nuevo de producción en esta revisión.
- **Pruebas ejecutadas por Codex:** `mvn test-compile` con JDK 17.0.21 terminó en `BUILD SUCCESS` tras ampliar la prueba. `git diff --check` pasó. No se ejecutó Maven completo ni conexión PostgreSQL en esta intervención porque las variables `LAEXCLUSIVA_TEST_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` no estaban disponibles en el proceso actual. El reporte PostgreSQL previo no corresponde a las nuevas aserciones y no se atribuye a ellas.
- **Errores encontrados y resolución:** ninguno de compilación; no hubo corrección de producción. No se conectó ni se modificó base alguna durante esta intervención.
- **Commit asociado:** `b54db35` (`test(sales-jdbc): cover PostgreSQL payment integrity cases`). La actualización documental que registra esta entrada se incluye en el commit documental de cierre.
- **Limitaciones y validación humana pendiente:** ejecutar de nuevo `mvn clean verify` con variables configuradas localmente y verificando que la URL es `la_exclusiva_test`; revisar los reportes de la prueba PostgreSQL ampliada. Hasta entonces, la rama no debe considerarse lista para publicar el PR. La integración de `SaleService` sigue pendiente de acordar la generación/permanencia de claves y los límites transaccionales de inventario/cartera.
