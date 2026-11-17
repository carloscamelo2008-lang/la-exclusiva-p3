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

## Registro 11

- **Fecha:** 2026-11-13.
- **Herramienta:** Codex en la aplicación de escritorio, Git y lectura de reportes locales; ejecución Maven realizada personalmente por Carlos en PowerShell.
- **Fase / tarea:** validación final de `feature/sales-jdbc` y preparación de publicación del PR.
- **Objetivo:** registrar evidencia definitiva de la suite con PostgreSQL real después de ampliar la prueba de integridad.
- **Resumen fiel del encargo:** verificar rama, HEAD y Surefire sin exponer secretos; registrar la ejecución de Carlos; hacer un microcommit documental; publicar la rama y abrir PR hacia `develop` sin fusionarlo.
- **Ejecución informada por Carlos:** 2026-11-13 a las 12:13, hora Colombia, `C:\apache-maven-3.10.0\bin\mvn.cmd clean verify`, Microsoft JDK 17.0.18 y URL exclusivamente `jdbc:postgresql://localhost:5432/la_exclusiva_test`. Carlos informó `BUILD SUCCESS`, JAR generado, 157 pruebas, 0 fallos, 0 errores y 0 omitidas.
- **Evidencia local revisada por Codex:** 18 XML de Surefire suman 157 pruebas con 0 fallos, 0 errores y 0 omisiones; el XML/TXT de `SaleRepositoryJdbcPostgresIntegrationTest` registra 1 ejecutada, 0 fallos, 0 errores y 0 omitidas, con marca temporal de las 12:13. El JAR `target/la-exclusiva-p3-1.0-SNAPSHOT.jar` también tiene marca temporal posterior. El código del test valida que la URL use el nombre exacto `la_exclusiva_test`; no se leyeron ni imprimieron credenciales.
- **Clasificación:** 1 prueba PostgreSQL real aprobada y 156 pruebas unitarias/JDBC simuladas aprobadas. La prueba real incluye persistencia de snapshots, pagos con la misma tupla y claves distintas, reintento con reloj posterior, conflicto de importe, rollback de inserción parcial, restricción única, concurrencia con dos conexiones y protección del saldo.
- **Archivos documentales actualizados:** `database/sales-jdbc-integration.md` y esta bitácora. No se cambiaron código ni esquema en esta intervención documental.
- **Errores y resolución:** ninguno. No se ejecutaron conexiones, migraciones ni escrituras de base por Codex en esta intervención; la ejecución PostgreSQL fue realizada por Carlos.
- **Commit asociado:** commit documental de esta entrada; el resultado se incluye con los cambios técnicos existentes de `feature/sales-jdbc`.
- **Limitaciones restantes:** `SaleService` y `Main` continúan usando repositorios en memoria. La integración JDBC general aún depende de acordar claves estables a nivel de caso de uso y de compartir unidad transaccional con inventario/cartera.

## Registro 12

- **Fecha:** 2026-11-16.
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git y Maven 3.10.0 con JDK 17.0.21.
- **Fase / tarea:** consulta JDBC de cartera de ventas; rama `feature/customer-debt-jdbc`, creada desde `develop` actualizado al merge `463da2f` del PR #11.
- **Objetivo:** ofrecer un puerto de solo lectura que calcule deuda pendiente asociada a ventas, sin tocar servicios ni módulos de otros compañeros.
- **Resumen fiel del encargo:** sincronizar `develop` por fast-forward, confirmar PR #11, crear la rama, inspeccionar `CustomerService`, `SaleService` y esquema, implementar puerto y adaptador JDBC, probar el cálculo y documentar cómo sustituir posteriormente el mapa `saleDebts`; no conectar servicios/Main, no cambiar contratos de inventario ni ejecutar migraciones.
- **Archivos creados:** `src/main/java/com/laexclusiva/repository/CustomerDebtQuery.java`, `src/main/java/com/laexclusiva/repository/jdbc/CustomerDebtQueryJdbc.java`, `src/test/java/com/laexclusiva/persistence/jdbc/CustomerDebtQueryJdbcTest.java` y `src/test/java/com/laexclusiva/persistence/jdbc/CustomerDebtQueryJdbcPostgresIntegrationTest.java`.
- **Archivos actualizados:** `database/sales-jdbc-integration.md`, `database/jdbc-integration-contract.md` y esta bitácora.
- **Decisiones técnicas:** el adaptador agrega `sale_line` y `sale_payment` separadamente por venta antes de agregarlos por cliente, evitando multiplicación entre relaciones uno-a-muchos. Calcula con los precios históricos, no lee `customer.balance`, devuelve cero sin ventas y solo consulta. El proveedor compartido reutiliza la conexión de una transacción exterior. Se documentó la futura distinción `deuda general = customer.balance - deuda de ventas`; no se modificó `CustomerService`.
- **Pruebas:** la ejecución dirigida con Java 17.0.21/Maven 3.10.0 registró 6 pruebas: 5 JDBC simuladas aprobadas y 1 integración PostgreSQL omitida por ausencia de variables en este proceso. `mvn clean verify` finalizó `BUILD SUCCESS`: 163 pruebas, 161 aprobadas, 0 fallidas, 0 errores y 2 omitidas; ambas son las pruebas PostgreSQL opcionales de ventas/cartera. No se conectó ni modificó ninguna base. `git diff --check` pasó antes de los commits.
- **Advertencias:** 6 problemas de modelo efectivo para cada dependencia JavaFX y el aviso de configuración no modular durante la prueba JavaFX; no afectaron la compilación ni las pruebas.
- **Commits asociados:** `1f4cb5e` (`feat(customer-debt-jdbc): add sales debt query port`) y `5e9971e` (`test(customer-debt-jdbc): verify sales debt query`). La documentación y esta bitácora se incluyen en el commit documental de cierre.
- **Limitaciones y pendientes:** la prueba PostgreSQL está preparada pero no fue ejecutada; requiere `LAEXCLUSIVA_TEST_DB_URL` apuntando exactamente a `la_exclusiva_test` y V001/V002 ya aplicadas. No se consultaron credenciales ni se ejecutaron migraciones. `CustomerService`, `SaleService` y `Main` siguen sin integración JDBC. La sustitución del mapa requiere una unidad transaccional compartida con los ajustes de `customer.balance` y las escrituras de ventas.

## Registro 13

- **Fecha:** 2026-11-16, 13:08 (Colombia).
- **Herramienta:** Maven 3.10.0 y Microsoft JDK 17.0.18 para la ejecución realizada por Carlos; Codex en la aplicación de escritorio, PowerShell y Git para inspeccionar reportes y documentar el resultado.
- **Fase / tarea:** validación definitiva de `feature/customer-debt-jdbc` antes de publicar el PR.
- **Resumen fiel del encargo:** confirmar rama, estado limpio y evidencia Surefire; registrar la ejecución completa con las dos integraciones PostgreSQL aprobadas; hacer un commit documental, publicar la rama y abrir un PR hacia `develop` sin fusionarlo.
- **Ejecución informada por Carlos:** `mvn clean verify` terminó en `BUILD SUCCESS`, con 163 pruebas, 0 fallidas, 0 errores y 0 omitidas; JAR generado. La base utilizada fue exclusivamente `la_exclusiva_test`. No se solicitaron ni registraron credenciales.
- **Evidencia local revisada por Codex:** los 20 reportes XML suman 163 pruebas, 0 fallidas, 0 errores y 0 omitidas. `CustomerDebtQueryJdbcPostgresIntegrationTest` y `SaleRepositoryJdbcPostgresIntegrationTest` registran cada una 1 prueba aprobada y ninguna omitida. Los reportes indican Java 17.0.18 y zona horaria `America/Bogota`; sus marcas de modificación y la del JAR corresponden a las 13:08. Codex no repitió las pruebas ni se conectó a la base.
- **Archivos actualizados:** `database/sales-jdbc-integration.md` y esta bitácora. `database/jdbc-integration-contract.md` no requería cambios porque no cambió el contrato del puerto.
- **Decisiones y alcance:** se conserva el dato de que la ejecución fue realizada por Carlos y que Codex verificó los reportes. No se modificó código, esquema ni servicio, y no se ejecutaron migraciones.
- **Errores y advertencias:** no hubo fallos en los reportes. La ejecución no registra pruebas omitidas. Esta intervención documental no repitió Maven, por lo que no generó advertencias de compilación nuevas.
- **Commit asociado:** microcommit documental de cierre con esta entrada y la evidencia de Surefire.
- **Pendientes humanos:** revisar el PR. `CustomerService`, `SaleService` y `Main` todavía no usan `CustomerDebtQuery`; la integración requiere coordinar ventas, pagos, saldo de cliente e inventario bajo una transacción compartida.

## Registro 14

- **Fecha:** 2026-11-16, aproximadamente 14:40 (Colombia).
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git, Maven 3.10.0 y JDK 17.0.21.
- **Fase / tarea:** corrección del cálculo de cartera para que un sobrepago histórico no compense deuda de otra venta en `feature/customer-debt-jdbc`, PR #12.
- **Resumen fiel del encargo:** aplicar `GREATEST(total_venta - pagos, 0)` por venta, añadir regresiones simulada y PostgreSQL, conservar la validación normal de abonos, actualizar documentación y bitácora, ejecutar pruebas con Java 17 y publicar la actualización del PR sin fusionarlo.
- **Archivos modificados:** `CustomerDebtQueryJdbc.java`, `CustomerDebtQueryJdbcTest.java`, `CustomerDebtQueryJdbcPostgresIntegrationTest.java`, `database/sales-jdbc-integration.md`, `database/jdbc-integration-contract.md`, `database/modelo-relacional.md`, `database/diccionario-datos.md` y esta bitácora.
- **Decisión técnica:** las CTE siguen agregando líneas y pagos separadamente por venta; `GREATEST(..., 0)` se aplica después de restarlos y antes de la suma por cliente. El fixture PostgreSQL crea por SQL directo una venta sobrepagada solo para representar una inconsistencia histórica; su limpieza se restringe a sus IDs aleatorios. Un sobrepago persistido debe detectarse y corregirse, no convertirse en crédito automático. `SalePaymentAppender` permanece sin cambios y conserva el bloqueo y rechazo preventivo.
- **Pruebas ejecutadas por Codex:** con JDK 17.0.21 y Maven 3.10.0, la ejecución dirigida contabilizó 25 pruebas: 23 aprobadas, 0 fallidas, 0 errores y 2 omitidas. `mvn clean verify` terminó en `BUILD SUCCESS`: 164 pruebas, 162 aprobadas, 0 fallidas, 0 errores y 2 omitidas. Las omitidas son `CustomerDebtQueryJdbcPostgresIntegrationTest` y `SaleRepositoryJdbcPostgresIntegrationTest`, porque las tres variables PostgreSQL no estaban configuradas en el proceso. No se conectó a PostgreSQL ni se aplicaron migraciones.
- **Errores y resolución:** una compilación inicial falló porque el fixture trató `SalePresentation.UNIT` como enum y llamó `.name()`; se inspeccionó su contrato y se corrigió a `.code()`. La compilación y las ejecuciones posteriores pasaron. Maven mostró las advertencias existentes de modelo efectivo JavaFX y de configuración JavaFX no modular.
- **Commits asociados:** `246e397` (`fix(customer-debt-jdbc): clamp debt per sale`) y `45d183e` (`test(customer-debt-jdbc): cover persisted overpayment`); la documentación y esta entrada se registran en el microcommit documental de cierre.
- **Limitaciones y pendientes humanos:** no hubo prueba PostgreSQL real en esta ejecución. Para ejecutarla se requiere configurar solo `LAEXCLUSIVA_TEST_DB_URL` con `la_exclusiva_test`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` en PowerShell; V001 y V002 deben estar aplicadas previamente. `CustomerService`, `SaleService` y `Main` siguen sin conectarse al puerto.

## Registro 15

- **Fecha:** 2026-11-16, aproximadamente 15:11 (Colombia).
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git, Maven 3.10.0 y JDK 17.0.21.
- **Fase / tarea:** sincronización de `feature/customer-debt-jdbc` con el PR #13 de JavaFX antes de la validación final del PR #12.
- **Resumen fiel del encargo:** verificar el árbol local, hacer `git fetch origin`, confirmar el merge `0a9f72f` del PR #13, integrar `origin/develop` mediante merge normal, validar cartera y pantallas JavaFX, ejecutar las pruebas disponibles y actualizar/preservar el PR #12 sin fusionarlo.
- **Integración:** el árbol estaba limpio y la rama estaba en `1cb78ef`. `origin/develop` se actualizó a `0a9f72f`; se integró con el merge commit `41e8488` (`Merge remote-tracking branch 'origin/develop' into feature/customer-debt-jdbc`). No hubo conflictos. El merge preservó los commits JDBC y añadió los controladores, vistas FXML y pruebas del PR #13; Codex no reescribió clases de esos cambios.
- **Archivos documentales actualizados por Codex:** `database/sales-jdbc-integration.md` y esta bitácora para registrar el merge y la verificación posterior.
- **Verificación funcional:** `CustomerDebtQueryJdbc` conserva el `GREATEST` por venta y sus regresiones simuladas/PostgreSQL continúan presentes. Se verificó la presencia de las nuevas vistas FXML. `git diff --check` pasó.
- **Pruebas:** con JDK 17.0.21/Maven 3.10.0, `mvn clean verify` terminó en `BUILD SUCCESS`: 181 pruebas contabilizadas, 179 aprobadas, 0 fallidas, 0 errores y 2 omitidas. Las omitidas son `CustomerDebtQueryJdbcPostgresIntegrationTest` y `SaleRepositoryJdbcPostgresIntegrationTest`; faltaban las tres variables de conexión en el proceso. No se conectó a PostgreSQL ni se ejecutaron migraciones.
- **Errores y advertencias:** no hubo conflictos ni fallos de pruebas. Maven informó advertencias preexistentes de modelo efectivo de JavaFX y configuración JavaFX no modular; los logs de excepciones de consulta corresponden a escenarios de fallo simulados en `AuthenticationServiceTest`.
- **Commits asociados:** merge `41e8488` con `origin/develop` (`0a9f72f`), más el commit documental que registra esta entrada. La rama queda pendiente de publicar y de revisar los checks nuevos de GitHub Actions.
- **Pendientes:** ejecutar las dos pruebas PostgreSQL reales únicamente contra `la_exclusiva_test` con V001/V002 ya aplicadas. No fusionar el PR #12.

## Registro 16

- **Fecha:** 2026-11-16.
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell, Git y Maven 3.10.0 con Temurin JDK 17.0.21.
- **Fase / tarea:** persistencia JDBC de clientes y vendedores en la rama `feature/customers-sellers-jdbc`, creada desde `develop` actualizado al merge `a2d83b9616c8d6e429a27c1ff9d7e8bdaec3d151` del PR #12.
- **Objetivo:** implementar los repositorios JDBC compatibles con las interfaces actuales y un puerto explícito de ajuste atómico del saldo, con pruebas, documentación y commits pequeños; no activar los adaptadores en JavaFX ni modificar servicios de ventas.
- **Resumen fiel del encargo:** sincronizar `develop`, crear la rama, conservar el funcionamiento en memoria, implementar repositorios de clientes/vendedores y ajustes concurrentes de saldo, probar operaciones CRUD, duplicados, errores y rollback, documentar la futura transacción compartida y no ejecutar migraciones, cambiar bases, hacer push ni merge.
- **Archivos creados:** `src/main/java/com/laexclusiva/repository/CustomerBalanceAdjuster.java`, `src/main/java/com/laexclusiva/repository/jdbc/CustomerBalanceAdjusterJdbc.java`, `CustomerRepositoryJdbc.java`, `SellerRepositoryJdbc.java`, `src/test/java/com/laexclusiva/persistence/jdbc/CustomerSellerJdbcTest.java` y `CustomerSellerJdbcPostgresIntegrationTest.java`.
- **Archivos actualizados:** `database/jdbc-integration-contract.md`, `database/sales-jdbc-integration.md` y esta bitácora.
- **Decisiones técnicas asistidas por Codex:** los repositorios usan el `JdbcConnectionProvider` compartido, SQL parametrizado y los tipos/límites de V001. `save` inserta y rechaza IDs duplicados sin upsert. El repositorio JDBC de clientes conserva el comportamiento de reemplazo de snapshot que tiene la implementación en memoria, incluido `balance`; se documentó que un snapshot antiguo puede pisar ajustes concurrentes. Para deudas y pagos se añadió `CustomerBalanceAdjuster`; la reducción usa `UPDATE ... WHERE balance >= ? RETURNING balance`, y ambas operaciones se pueden unir a una transacción exterior mediante el mismo proveedor. No se conectaron `CustomerService`, `SaleService`, `Main` ni `App.java`; no se tocaron módulos de Jesús o Daniel.
- **Pruebas ejecutadas por Codex:** con Temurin JDK 17.0.21 y Maven 3.10.0, `mvn -Dtest=CustomerSellerJdbcTest,CustomerSellerJdbcPostgresIntegrationTest test` contabilizó 9 pruebas: 8 simuladas aprobadas y la integración PostgreSQL omitida. `mvn clean verify` terminó en `BUILD SUCCESS`: 190 pruebas, 187 aprobadas, 0 fallidas, 0 errores y 3 omitidas. Se omitieron las integraciones PostgreSQL de ventas, cartera y clientes/vendedores porque las variables de conexión no estaban configuradas. No se conectó a PostgreSQL ni se ejecutaron migraciones. Se generó el JAR.
- **Errores y advertencias:** una primera ejecución dirigida falló por una aserción del controlador simulado que leía la sentencia de existencia ejecutada después del intento de abono; se ajustó para observar la sentencia `UPDATE` de reducción y la siguiente ejecución dirigida pasó. Maven también mostró advertencias de modelo efectivo de las dependencias JavaFX y de configuración JavaFX no modular; los errores de consulta que aparecen en `AuthenticationServiceTest` son escenarios simulados que no causaron fallos.
- **Commits asociados:** `29d0f04` (`feat(customers-sellers-jdbc): add JDBC repositories and atomic balance port`) y `a3fa81a` (`test(customers-sellers-jdbc): cover repositories and atomic balances`). La actualización documental y este registro se incluyen en un microcommit documental de cierre.
- **Limitaciones y pendientes humanos:** ejecutar `CustomerSellerJdbcPostgresIntegrationTest` con las variables configuradas para conectar exclusivamente a `la_exclusiva_test`, con V001 aplicada; revisar contra PostgreSQL real la concurrencia, la atomicidad y la limpieza de fixtures. Confirmar que el contrato de reemplazo completo de `CustomerRepositoryJdbc.update` es aceptable; para operaciones financieras concurrentes debe usarse el puerto nuevo, no snapshots. Los servicios y la composición JavaFX permanecen en memoria y requieren una fase de integración transaccional posterior.
- **Validación humana:** este registro describe cambios y resultados realmente observados por Codex. Carlos aún debe revisar los contratos y ejecutar la prueba PostgreSQL real antes de publicar o integrar la rama.

## Registro 17

- **Fecha:** 2026-11-16.
- **Herramienta:** Codex en la aplicación de escritorio, PowerShell y Git; Maven/Java/PostgreSQL ejecutados personalmente por Carlos.
- **Fase / tarea:** cierre de persistencia JDBC de clientes y vendedores en `feature/customers-sellers-jdbc`, antes de publicar el PR hacia `develop`.
- **Objetivo:** registrar la validación definitiva contra PostgreSQL y revisar el riesgo de actualización por snapshots sin alterar el contrato ni el código.
- **Resumen fiel del encargo:** confirmar rama y árbol limpio; inspeccionar el contrato y las pruebas de `CustomerRepositoryJdbc.update(Customer)`; conservar la semántica existente, documentar cualquier riesgo irreducible, registrar la ejecución real de Maven, crear un microcommit documental, publicar la rama y abrir un PR sin fusionarlo.
- **Revisión del snapshot:** `CustomerRepositoryJdbc.update` reemplaza también `balance`, en concordancia con el repositorio en memoria. `CustomerSellerJdbcTest.customerRepositoryInsertsQueriesAndUpdatesWithoutReplacingDuplicateIds` verifica que la actualización reemplaza el saldo. Un snapshot antiguo podría pisar ajustes concurrentes; no se cambió el contrato. Se mantiene como limitación y la futura integración debe aplicar pagos y demás ajustes financieros exclusivamente mediante `CustomerBalanceAdjuster` dentro de una transacción compartida.
- **Ejecución informada por Carlos:** el 2026-11-16 a las 22:45 (Colombia), `mvn clean verify`, Java 17, PostgreSQL dedicado `la_exclusiva_test`: `BUILD SUCCESS`, 190 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas.
- **Evidencia local inspeccionada por Codex:** los 22 reportes XML de Surefire suman 190/190 con cero fallos, errores u omisiones. `CustomerSellerJdbcPostgresIntegrationTest`, `CustomerDebtQueryJdbcPostgresIntegrationTest` y `SaleRepositoryJdbcPostgresIntegrationTest` registran una prueba aprobada cada una y marcas de modificación entre las 22:45:23 y 22:45:28. Codex no volvió a ejecutar Maven ni abrió conexiones PostgreSQL. El código de configuración de la prueba valida que la URL de integración nombre exclusivamente `la_exclusiva_test`. No se leyeron ni registraron credenciales.
- **Archivos actualizados:** `database/jdbc-integration-contract.md`, `database/sales-jdbc-integration.md` y esta bitácora. No se modificó código fuente, pruebas, migraciones, servicios ni módulos de otros compañeros.
- **Errores y advertencias:** no se encontraron fallos en Surefire. `gh` no está instalado en este entorno; la publicación y el PR se gestionarán mediante la sesión de GitHub disponible si está autenticada. No se ejecutaron migraciones ni operaciones de base de datos.
- **Commits asociados:** `29d0f04` (adaptadores y puerto), `a3fa81a` (pruebas) y `a3d1e26` (documentación previa); la evidencia definitiva se registra en el microcommit documental de este cierre.
- **Pendientes humanos:** revisar la limitación de snapshots antes de conectar servicios y conservar el uso exclusivo de `CustomerBalanceAdjuster` para operaciones financieras concurrentes. El PR no debe fusionarse hasta revisión del equipo.
