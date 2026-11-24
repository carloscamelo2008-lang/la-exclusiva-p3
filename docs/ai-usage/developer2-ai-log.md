# Bitácora de uso de IA – Desarrollador 2

**Integrante:** Daniel Josué Arrieta Fontalvo  
**Rol inicial:** clientes, fiados y abonos  
**Proyecto:** La Exclusiva  

## Registro de interacciones

### Interacción 1
* **Fecha:** 26/10/2026
* **Herramienta:** Gemini
* **Fase, tarea y rama:** Fase 3 – Setup inicial y modelos de dominio (`feature/clients-module`)
* **Objetivo de la consulta:** Revisión y validación de la estructura de las clases de dominio Customer, Seller y PersonValidator asegurando el cumplimiento de las convenciones del equipo.
* **Resumen de la respuesta recibida:** La herramienta sirvió de soporte técnico para la estructura inicial de clases y validaciones.
* **Qué se aceptó:** Tomó las sugerencias de la IA para refinarlas e integrarlas, asegurando que la lógica de validación respetara el desacoplamiento y las reglas de negocio.
* **Verificación realizada:** Compilacion limpia mediante Clean and Build con Maven.
* **Commit o Pull Request relacionado:** Commit local inicial en `feature/clients-module`.

---

### Interacción 2
* **Fecha:** 26/10/2026
* **Herramienta:** Gemini
* **Fase, tarea y rama:** Fase 3 – Ajustes de arquitectura y precisión monetaria (`feature/clients-module`)
* **Objetivo de la consulta:** Apoyo para refactorizar la precisión monetaria a BigDecimal, desacoplamiento con interfaces de repositorios y adición de pruebas unitarias exhaustivas.
* **Resumen de la respuesta recibida:** Se generaron propuestas para migrar saldos a BigDecimal, implementar contratos de interfaz e impedir sobreescritura de registros en repositorios.
* **Qué se aceptó:** Aceptó e integró el uso de BigDecimal.valueOf(), las interfaces CustomerRepositoryInterface y SellerRepositoryInterface, manteniendo el control del flujo y reglas de negocio.
* **Verificación realizada:** Ejecución de pruebas unitarias JUnit e integración continua con `mvn clean verify`.
* **Commit o Pull Request relacionado:** Commit de actualización en PR `feature/clients-module`.

---

### Interacción 3
* **Fecha:** 29/10/2026
* **Herramienta:** Gemini
* **Fase, tarea y rama:** Sprint 3 – Módulo de Autenticación, Roles, Permisos y Seguridad (`feature/authentication-roles`)
* **Objetivo de la consulta:** Guía técnica para implementar el modelo de sesión (`UserSession`), la utilitaria de cifrado PBKDF2 (`PasswordHasher`), la persistencia en memoria (`InMemoryUserRepository`) y los servicios de autenticación y autorización.
* **Resumen de la respuesta recibida:** Propuesta de arquitectura y código en Java con validaciones de credenciales, derivación de claves con `PBKDF2WithHmacSHA256` y control de acceso centralizado por rol (`ADMINISTRATOR` y `WORKER`).
* **Qué se acepta, modifica o descarta y por qué:** Se aceptó el diseño de las clases y servicios propuestos, adaptando los paquetes a la estructura del proyecto (`com.laexclusiva.model`, `repository` y `service`) y manteniendo la consistencia con las reglas de negocio del equipo.
* **Verificación realizada:** Integración de componentes, organización en micro commits independientes por módulo y validación exitosa mediante `mvn clean verify` (`BUILD SUCCESS`).
* **Commit o Pull Request relacionado:** Micro commits independientes en `feature/authentication-roles` y apertura de Pull Request hacia `develop`.

---

### Interacción 4
* **Fecha:** 16/11/2026
* **Herramienta:** Codex
* **Fase, tarea y rama:** Fase 3 – Permisos del menú principal en JavaFX (`feature/javafx-login-navigation`)
* **Objetivo de la consulta:** Usé la IA como apoyo para revisar y corregir la visibilidad del botón Vendedores según los permisos del usuario.
* **Resumen de la respuesta recibida:** La IA encontró que el menú mostraba Vendedores incluso para el trabajador y propuso consultar el permiso `VIEW_SELLERS` de la sesión para ocultar el botón y quitarlo de la distribución del menú cuando no correspondiera.
* **Qué se acepta, modifica o descarta y por qué:** Acepté usar el permiso que ya existe y conservar el comportamiento de Gestión de Usuarios. También aprobé agregar pruebas para comprobar cómo se muestran los dos botones con sesiones de administrador y trabajador.
* **Verificación realizada:** Codex ejecutó `mvn -Dtest=AuthenticationServiceTest test` y pasaron las 7 pruebas. Después ejecutó `mvn clean verify` con JDK 25.0.2 y compilación para Java 17. Pasaron las 138 pruebas, sin fallos, errores ni omisiones.
* **Commit o Pull Request relacionado:** Microcommit `fix(javafx): gate seller menu by permission` pendiente de creación.

---

### Interacción 5
* **Fecha:** 16/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Navegación JavaFX al listado de Clientes (`feature/javafx-login-navigation`).
* **Objetivo de la consulta:** Quise apoyarme en la IA para conectar el menú con la consulta de clientes, conservar la sesión al cambiar de pantalla y respetar los permisos de cada usuario.
* **Resumen de la respuesta recibida:** Codex propuso abrir Clientes en el panel central y conservar el menú lateral y la sesión al volver a Resumen / Inicio. Implementó una tabla con ID, nombre, teléfono y dirección, reutilizó `CustomerService` con el `CustomerRepository` existente y comprobó `VIEW_CUSTOMER` antes de consultar. También añadió mensajes para una lista vacía y para errores de consulta.
* **Qué se acepta, modifica o descarta y por qué:** Revisé y acepté la solución de solo lectura para completar la navegación y la consulta con los servicios que ya tenemos. Dejé la persistencia y las funcionalidades de otros módulos fuera del cambio.
* **Verificación realizada:** Codex ejecutó `mvn -Dtest=AuthenticationServiceTest test` y se aprobaron las 12 pruebas dirigidas. También ejecutó `mvn clean verify` y se aprobaron las 143 pruebas generales. Ambas ejecuciones usaron JDK 25.0.2, compilación para Java 17 y JavaFX 17.0.2. Queda pendiente verificar la ejecución con JDK 17.
* **Commit o Pull Request relacionado:** Commit de desarrollo `63a0276035c3750abf4b34f4dd9cba95f30d4a27`.

---

### Interacción 6
* **Fecha:** 16/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Navegación JavaFX al listado de Vendedores (`feature/javafx-login-navigation`).
* **Objetivo de la consulta:** Me apoyé en la IA para conectar el menú con la consulta de vendedores, conservar la sesión al cambiar de pantalla y respetar los permisos de cada usuario.
* **Resumen de la respuesta recibida:** Codex conectó Vendedores a una tabla de solo lectura con ID, nombre, teléfono y dirección. Reutilizó `SellerService` y el repositorio existente, y comprobó `VIEW_SELLERS` antes de cargar la vista o consultar. También añadió mensajes para la lista vacía y los errores de consulta.
* **Qué se acepta, modifica o descarta y por qué:** Revisé y acepté el listado de solo lectura porque cubre la consulta y mantiene la navegación que ya teníamos. Dejé fuera del cambio la persistencia y los módulos de mis compañeros.
* **Verificación realizada:** Codex ejecutó `mvn -Dtest=AuthenticationServiceTest test` y se aprobaron las 17 pruebas dirigidas. También ejecutó `mvn clean verify` y se aprobaron las 148 pruebas generales. Ambas ejecuciones usaron JDK 25.0.2, compilación para Java 17 y JavaFX 17.0.2. Queda pendiente verificar la ejecución con JDK 17.
* **Commit o Pull Request relacionado:** Commit de desarrollo `d992a9464b8a1192d3a79c08b2b37f1cddd276b1`.

---

### Interacción 7
* **Fecha:** 16/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Navegación JavaFX a Cartera pendiente (`feature/javafx-login-navigation`).
* **Objetivo de la consulta:** Me apoyé en la IA para conectar el menú con una pantalla de consulta de cartera, reutilizar el servicio de clientes y aplicar los permisos sin modificar la lógica de negocio.
* **Resumen de la respuesta recibida:** Codex implementó una tabla de solo lectura con ID, nombre y saldo pendiente en COP. Filtró los clientes con saldo positivo mediante `BigDecimal`, reutilizó `CustomerService`, verificó `VIEW_PORTFOLIO` antes de consultar y mantuvo la sesión y la navegación.
* **Qué se acepta, modifica o descarta y por qué:** Revisé y acepté la consulta de cartera porque sigue el comportamiento de la consola y no invade los módulos de mis compañeros. Dejé pendiente el registro de abonos debido a las dependencias con ventas y persistencia JDBC.
* **Verificación realizada:** Codex ejecutó `mvn -Dtest=AuthenticationServiceTest test` con 22 pruebas aprobadas y `mvn clean verify` con 153 pruebas aprobadas. `git diff --check` pasó. Se utilizó JDK 25.0.2, compilación para Java 17 y JavaFX 17.0.2. Queda pendiente ejecutar la aplicación con JDK 17.
* **Commit o Pull Request relacionado:** `cf3704f43f77813160c31c45f8bb16193917d090`.

---

### Interacción 8 – Actividad 5

* **Fecha:** 17/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Navegación y sesión JavaFX (`feature/javafx-navigation-ux`).
* **Objetivo de la consulta:** Me apoyé en Codex para mejorar el cierre de sesión, la navegación, los permisos y la presentación del menú.
* **Resumen de la respuesta recibida:** Codex implementó el cambio de usuario, el resaltado del menú y Gestión de Usuarios deshabilitada. Aclaró que Cartera es solo consulta y corrigió un fallo al restaurar la ventana.
* **Qué se acepta, modifica o descarta y por qué:** Revisé la propuesta y aprobé las mejoras para facilitar el uso, conservando los permisos. Dejé pendientes los abonos y la integración JDBC.
* **Verificación realizada:** Codex ejecutó pruebas y revisó visualmente la aplicación con cuentas temporales. GitHub Actions pasó con Java 17: 196 pruebas aprobadas y 3 omitidas. Quedan pendientes datos largos y otros escalados.
* **Commit o Pull Request relacionado:** [PR #17](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/17). Desarrollo: `d1872c3`; corrección: `e8f546b`; integración de develop: `f2c66cf`.

---

### Interacción 9 – Planificación de identidad y administración

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Inspección y propuesta de contratos (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para organizar mi módulo de identidad según el plan del equipo.
* **Resumen de la respuesta recibida:** Codex revisó el PDF, el código y GitHub. Creó la rama desde `origin/develop` y preparó una propuesta sobre usuarios JDBC, autenticación, bootstrap, sesiones, gestión JavaFX e ID automático de clientes.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé preparar la propuesta, sin implementar funcionalidades. Los contratos siguen pendientes de aprobación de Carlos y Jesús; dejé fuera los cambios de sus módulos y del menú central.
* **Verificación realizada:** Codex comprobó el árbol limpio, actualizó las referencias y revisó el diff. `git diff --check` pasó. No ejecutó pruebas ni modificó PostgreSQL.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 10 – Repositorio JDBC de usuarios

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Persistencia de usuarios (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para implementar y revisar el repositorio JDBC sin cambiar contratos ni conectar el login.
* **Resumen de la respuesta recibida:** Codex implementó y revisó el adaptador, el diagnóstico SQL seguro, la normalización, los hashes históricos y la limpieza de fixtures. Aisló la prueba de secuencia con tabla e identidad temporales y corrigió la comparación del wrapper mediante `pg_backend_pid()` para comprobar la misma sesión PostgreSQL.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé estas correcciones sin modificar módulos compartidos. Conservé la validación estricta de nuevas escrituras y dejé pendiente validar en PostgreSQL el límite operativo de 64 intentos de ID.
* **Verificación realizada:** Codex ejecutó 56 pruebas sin PostgreSQL: 42 del repositorio, 6 de usuarios y 8 de transacciones; todas aprobaron con JDK 25.0.2 y compilación para Java 17. Después ejecuté manualmente las cinco integraciones en `la_exclusiva_test` con PostgreSQL 18; aprobaron sin fallos, errores ni omisiones. Codex confirmó el informe de Surefire y revisó el diff. `git diff --check` pasó. No se ejecutó la suite completa ni se verificó con JDK 17.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 11 – Pruebas de autenticación con repositorio

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Pruebas unitarias de autenticación (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para completar la cobertura de autenticación sin conectar el login a PostgreSQL.
* **Resumen de la respuesta recibida:** Codex revisó las pruebas existentes y añadió casos de credenciales rechazadas, reconstrucción del servicio y cambio de usuario. Reforzó la comprobación del rol, vendedor y sesión invalidada.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé ampliar las pruebas existentes para evitar duplicarlas, conservando los contratos y el modo memoria. La reconstrucción usa el mismo repositorio en memoria; no demuestra persistencia entre procesos.
* **Verificación realizada:** Codex ejecutó 84 pruebas: 10 de usuarios, 32 de autenticación y 42 del repositorio JDBC simulado; todas aprobaron, sin fallos, errores ni omisiones. Usó JDK 25.0.2 y compilación para Java 17. Revisó el diff y `git diff --check`; no ejecutó PostgreSQL ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 12 – Composición independiente de identidad

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Composición de identidad (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar identidad en memoria y PostgreSQL sin conectar todavía la aplicación.
* **Resumen de la respuesta recibida:** Codex creó `IdentityServices`, que reúne repositorio y autenticación, reutiliza sus instancias y recibe el proveedor JDBC existente sin abrir conexiones al construirse.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé la composición aislada y sus pruebas, sin crear cuentas ni cambiar contratos. Dejé la integración con `App` y `ApplicationServices` pendiente; la aplicación aún no autentica mediante esta fábrica PostgreSQL.
* **Verificación realizada:** Codex ejecutó 103 pruebas: 9 nuevas de composición y 94 existentes de usuarios, autenticación, repositorio y transacciones; todas aprobaron, sin fallos, errores ni omisiones. Usó JDBC simulado, JDK 25.0.2 y compilación para Java 17. `git diff --check` pasó. No ejecutó PostgreSQL ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 13 – Migración de versión de seguridad

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Preparación de V104 (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar el versionado persistente de usuarios aprobado por Carlos, sin aplicar la migración.
* **Resumen de la respuesta recibida:** Codex creó V104 con columna, restricción y trigger para incrementar la versión al cambiar datos de seguridad. Añadió documentación mínima y controles estáticos.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé preparar estos archivos, conservando las migraciones anteriores y el código Java de identidad. Dejé la aplicación del esquema pendiente de autorización y la seguridad pendiente de revisión de Jesús; el bloqueo administrativo reservado no está implementado.
* **Verificación realizada:** Codex inspeccionó el SQL y ejecutó tres pruebas estáticas con Temurin 17.0.21; aprobaron sin fallos, errores ni omisiones. `git diff --check` pasó. No hubo conexiones ni ejecución SQL: el funcionamiento del trigger en PostgreSQL sigue pendiente de validar.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 14 – Modelo de versión de seguridad

* **Fecha:** 18/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Metadato de seguridad en usuarios (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para incorporar la versión de seguridad al modelo sin cambiar los constructores existentes.
* **Resumen de la respuesta recibida:** Codex añadió `securityVersion`, inicializado en cero, con lectura y asignación validada. Preparó pruebas de límites, atributos y compatibilidad.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé únicamente el modelo y sus pruebas. La versión es un metadato de persistencia, no un campo de formulario ni un permiso; dejé el incremento y el mapeo de repositorios para otra actividad.
* **Verificación realizada:** Codex ejecutó 101 pruebas con Temurin 17.0.21: 8 nuevas del modelo y 93 existentes de usuarios, autenticación, repositorio JDBC simulado y composición; todas aprobaron sin fallos, errores ni omisiones. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 15 – Versionado y aislamiento en memoria

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Repositorio de usuarios en memoria (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para proteger las cuentas en memoria frente a cambios accidentales y actualizaciones con versiones antiguas.
* **Resumen de la respuesta recibida:** Codex implementó copias defensivas, sincronización por repositorio y control de versión esperada. Añadió incrementos por cambios de seguridad, conservación de la fecha histórica y protección de IDs y desbordamientos.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé limitar el cambio al repositorio memory y sus pruebas. Dejé pendientes la revocación de sesiones, el último administrador y la revisión de Jesús; la versión no detecta conflictos entre cambios exclusivos de username.
* **Verificación realizada:** Codex ejecutó 79 pruebas requeridas y 15 regresiones de consola con Temurin 17.0.21: 94 aprobadas, sin fallos, errores ni omisiones. Incluyó 25 casos nuevos de copias, versiones y concurrencia con barreras. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 16 – Versionado de usuarios JDBC

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Versión esperada en JDBC (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para adaptar el repositorio JDBC a la versión de seguridad sin aplicar V104.
* **Resumen de la respuesta recibida:** Codex añadió el mapeo de versión, INSERT con metadatos devueltos y UPDATE condicionado por versión esperada. Reforzó conflictos, errores de cierre y pruebas simuladas.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé el adaptador y sus pruebas sin cambiar contratos ni otros módulos. Dejé el incremento a cargo del trigger y documenté resultados inciertos y versiones provisionales tras rollback. V104 y la revisión de Jesús siguen pendientes.
* **Verificación realizada:** Codex ejecutó 70 pruebas del adaptador y 97 regresiones con Temurin 17.0.21: 167 aprobadas, sin fallos, errores ni omisiones. Usó JDBC simulado y comprobó rollback-only. `git diff --check` pasó. No ejecutó PostgreSQL, migraciones ni la suite completa; el trigger no fue validado realmente.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 17 – Preparación de integraciones de versión de seguridad

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Pruebas PostgreSQL preparadas (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar la validación real de versiones sin conectarnos ni aplicar V104.
* **Resumen de la respuesta recibida:** Codex preparó 11 casos de trigger y adaptador, con fixtures exclusivos, comprobaciones de destino y esquema por conexión y concurrencia con tiempos límite. Conservó las cinco integraciones anteriores.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé preparar las pruebas y documentar sus límites. Dejé la ejecución pendiente de revisión y autorización; la comprobación de una fila anterior a V104 será manual durante su aplicación autorizada.
* **Verificación realizada:** Codex compiló con Temurin 17.0.21 y ejecutó 104 pruebas distintas sin PostgreSQL: 21 nuevas de seguridad simulada y 83 existentes; todas aprobaron sin fallos, errores ni omisiones. Repitió las 21 nuevas tras la revisión final. `git diff --check` pasó. No ejecutó integraciones, SQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

### Interacción 18 – Política del último administrador activo

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Política pura de administración (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar la regla que impide retirar al último administrador activo.
* **Resumen de la respuesta recibida:** Codex creó una política independiente y pruebas de desactivación, cambio de rol, promociones y datos inválidos.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé solo la política y sus pruebas. Dejé pendientes la integración, los bloqueos transaccionales y la revisión de Jesús; esta regla por sí sola no garantiza permisos ni concurrencia.
* **Verificación realizada:** Codex ejecutó 63 pruebas con Temurin 17.0.21: 20 nuevas y 43 regresiones, sin fallos, errores ni omisiones. Compiló con objetivo Java 17 y pasó `git diff --check`. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Frontera administrativa (microcommit 2)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Coordinación administrativa (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar una frontera común para las futuras operaciones de usuarios.
* **Resumen de la respuesta recibida:** Codex implementó sincronización sobre el repositorio en memoria y una frontera JDBC con transacción compartida, READ COMMITTED y el bloqueo reservado. Añadió pruebas sin conexión.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé solo la coordinación, sin operaciones administrativas ni integración. Quedan pendientes permisos, último administrador, revisión de Jesús y pruebas PostgreSQL; las escrituras externas no quedan protegidas.
* **Verificación realizada:** Codex corrigió una ambigüedad de compilación y, en la revisión final, reprodujo y corrigió la exposición de detalles SQL en errores. Ejecutó 159 pruebas con Temurin 17.0.21: 24 nuevas y 135 regresiones, sin fallos, errores ni omisiones. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Captura de sesión (microcommit 3A)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Identidad inmutable de sesión (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para conservar el ID y la versión con los que se construyó cada sesión.
* **Resumen de la respuesta recibida:** Codex añadió campos finales y accesores de lectura, validó IDs y versiones y preparó pruebas de cambios posteriores e invalidación.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé solo la sesión y sus pruebas, conservando el constructor y logout. Dejé pendiente comparar la captura con el repositorio; esto todavía no revoca sesiones por cambios administrativos.
* **Verificación realizada:** Codex ejecutó 108 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 17 nuevas y 91 regresiones de modelo, usuarios, autenticación, memoria, composición y consola; sin fallos, errores ni omisiones. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Revalidación administrativa (microcommit 3B)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Vigencia administrativa (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para comprobar la sesión administrativa contra la cuenta actual del repositorio.
* **Resumen de la respuesta recibida:** Codex detectó que faltaba distinguir invalidación y actividad del snapshot. Tras mi autorización añadió `isValid()` y un revalidador que usa el ID y la versión capturados y exige `MANAGE_USERS` en la cuenta recargada.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé esa ampliación sin cambiar autenticación ni fronteras. El futuro servicio deberá revalidar dentro de la frontera; todavía no hay operaciones administrativas ni integración automática.
* **Verificación realizada:** Codex ejecutó 172 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 20 nuevas y 152 regresiones, sin fallos, errores ni omisiones. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Rol y activación protegidos (microcommit 3C)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Operaciones administrativas (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para integrar cambios de rol y actividad con los componentes de seguridad ya preparados.
* **Resumen de la respuesta recibida:** Codex creó un servicio que revalida al actor, comprueba la versión del objetivo, copia sus datos y protege al último administrador antes de una única actualización. Devuelve un resultado sin hashes.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé solo estas dos operaciones y sus pruebas, sin integración en la aplicación. La protección exige emparejar repositorio y frontera; las escrituras directas siguen fuera del protocolo. Dejé pendientes PostgreSQL y la revisión de Jesús.
* **Verificación realizada:** Codex ejecutó 211 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 39 nuevas y 172 regresiones, sin fallos, errores ni omisiones. Incluyó competencia en memoria con barreras entre dos servicios. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Creación administrativa (microcommit 3D)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Alta protegida de cuentas (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para permitir que un administrador vigente cree cuentas mediante el servicio protegido.
* **Resumen de la respuesta recibida:** Codex añadió un request inmutable que oculta sus datos al imprimirse y reutilizó PBKDF2. La creación revalida al actor dentro de la frontera, guarda una vez y devuelve un resultado sin credenciales.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé cuentas de ambos roles, activas o inactivas, sin vendedor. Conservé el contrato actual de contraseñas y dejé pendientes la política de Jesús, la integración y PostgreSQL. No se añadió bootstrap ni reintentos.
* **Verificación realizada:** Codex ejecutó 308 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 27 nuevas y 281 regresiones, sin fallos, errores ni omisiones. Dos creaciones concurrentes dejaron una sola cuenta nueva. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Restablecimiento de contraseña (microcommit 3E)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Contraseña administrativa (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para incorporar el restablecimiento administrativo de contraseña dentro del servicio protegido.
* **Resumen de la respuesta recibida:** Codex reutilizó la revalidación, PBKDF2 y la carga con versión esperada. La operación cambia solo el hash mediante una copia y una actualización, sin contar administradores ni renovar sesiones.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé restablecimientos propios y ajenos sin contraseña anterior, conservando las reglas actuales. Dejé pendientes la revisión de Jesús, la integración y PostgreSQL; las sesiones antiguas se rechazan al revalidar, sin cancelar operaciones iniciadas.
* **Verificación realizada:** Codex ejecutó 331 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 23 nuevas y 308 regresiones, sin fallos, errores ni omisiones. Comprobó cambio propio, nuevo login y concurrencia en memoria con un único ganador. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Asignación de vendedor (microcommit 3F)

* **Fecha:** 19/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Vendedor de la cuenta (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para asignar o desvincular vendedores y evitar que dos cuentas activas compartan uno.
* **Resumen de la respuesta recibida:** Codex conservó el constructor anterior, añadió consulta opcional de vendedores y comprobó existencia y exclusividad dentro de la frontera. También protegió la activación sin consultar vendedores históricos.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé las reglas aprobadas por Carlos para ambos roles, incluidos vendedores compartidos por cuentas inactivas y desvinculación con null. No se corrigen duplicados históricos ni se protegen escrituras externas; la integración de Ventas y la revisión de Jesús siguen pendientes.
* **Verificación realizada:** Codex ejecutó 356 pruebas con Temurin 17.0.21+9 y objetivo Java 17: 25 nuevas y 331 regresiones, sin fallos, errores ni omisiones. Los tres escenarios concurrentes de memoria conservaron un solo propietario activo. `git diff --check` pasó. No ejecutó PostgreSQL, V104 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.

---

#### Continuación de la Interacción 18 – Exclusividad PostgreSQL (microcommit 4A)

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – V105 y conflictos JDBC (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para preparar la exclusividad del vendedor activo también ante escrituras externas.
* **Resumen de la respuesta recibida:** Codex preparó V105 independiente, el diagnóstico de duplicados y la clasificación del índice mediante metadatos PostgreSQL, con pruebas simuladas y estáticas.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el alcance aprobado por Carlos, conservando V104, los errores de ID y username y los módulos ajenos. Los duplicados requieren resolución humana; Carlos y Jesús deben revisar el SQL antes de autorizar su aplicación.
* **Verificación realizada:** Codex ejecutó 372 pruebas con Temurin 17.0.21+9 y objetivo Java 17: diez nuevas y 362 regresiones, sin fallos, errores ni omisiones. `git diff --check` pasó y las migraciones anteriores permanecieron intactas. No ejecutó PostgreSQL, V104, V105 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.
---

#### Continuación de la Interacción 18 – Política de contraseñas (microcommit 4B)

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Política centralizada (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para aplicar las reglas aprobadas a nuevas cuentas, restablecimientos y aprovisionamiento.
* **Resumen de la respuesta recibida:** Codex centralizó longitud Unicode y bloqueo local, conservó el secreto original y validó antes del hash y de crear cuentas demo.
* **Qué se acepta, modifica o descarta y por qué:** Acepté frases y espacios sin reglas de composición, manteniendo los hashes históricos. La lista inicial ilustrativa sigue pendiente de revisión de Jesús; dejé verify para 4C.
* **Verificación realizada:** Codex ejecutó 364 pruebas dirigidas con Temurin 17.0.21+9 y objetivo Java 17, sin fallos, errores ni omisiones; incluyó 24 de política, administración y arranque JavaFX. Revisó la consola sin probarla interactivamente. `git diff --check` pasó. No ejecutó la suite completa ni PostgreSQL, V104 o V105.
* **Commit o Pull Request relacionado:** Pendiente de creación.
#### Continuación de 4B – Correcciones de auditoría

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Corrección de política y Login (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Pedí corregir la incompatibilidad del Login con los espacios y reforzar las pruebas del recurso.
* **Resumen de la respuesta recibida:** Codex conservó la contraseña intacta en Login, acotó la lectura por línea y probó el fallo cerrado de validate con clases aisladas.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el ajuste mínimo autorizado por Carlos, sin cambiar autenticación, hashes o navegación. La lista permanece ilustrativa y pendiente de revisión de Jesús.
* **Verificación realizada:** Codex ejecutó 376 pruebas dirigidas con Temurin 17.0.21+9 y objetivo Java 17: cero fallos, errores u omisiones. Comprobó Login con espacios y contraseña histórica, dos llamadas tras fallo de carga y lectura acotada. `git diff --check` pasó. No ejecutó la suite completa, consola interactiva ni PostgreSQL.
* **Commit o Pull Request relacionado:** Pendiente de creación.
---

### Interacción 19 – Verificación segura de contraseñas (4C)

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Verificación PBKDF2 (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para revisar y endurecer verify sin cambiar su contrato ni los hashes existentes.
* **Resumen de la respuesta recibida:** Codex validó formato y límites antes de derivar, eliminó registros sensibles y limpió datos temporales, con pruebas históricas y fallos simulados.
* **Qué se acepta, modifica o descarta y por qué:** Acepté los límites autorizados por Carlos y mantener false ante rechazo o fallo criptográfico recuperable. No cambié la política ni inicié 4D; la revisión técnica de Jesús para 4C sigue pendiente.
* **Verificación realizada:** Codex ejecutó 403 pruebas dirigidas con Temurin 17.0.21+9 y objetivo Java 17: 27 nuevas y 376 regresiones, sin fallos, errores u omisiones. Verificó el fixture histórico; el máximo de iteraciones se comprobó con derivación simulada. git diff --check pasó. No ejecutó la suite completa ni PostgreSQL, V104 o V105.
* **Commit o Pull Request relacionado:** Pendiente de creación.
#### Continuación de la Interacción 19 – Refuerzo de pruebas de 4C

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Pruebas de memoria y Base64 (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Pedí reforzar las dos observaciones de auditoría antes de la revisión externa.
* **Resumen de la respuesta recibida:** Codex comprobó borrado de arreglos accesibles con datos no nulos en éxito y fallo recuperable, y construyó el Base64 sin padding explícitamente.
* **Qué se acepta, modifica o descarta y por qué:** Acepté mejorar solo las pruebas y documentar que PBEKeySpec y el resultado esperado se revisan por inspección, sin prometer una observación de memoria inexistente. No se cambió producción.
* **Verificación realizada:** Codex ejecutó 404 pruebas dirigidas con Temurin 17.0.21+9 y objetivo Java 17: 28 del hasher y 376 regresiones, sin fallos, errores u omisiones. git diff --check pasó. No ejecutó PostgreSQL, V104/V105 ni la suite completa.
* **Commit o Pull Request relacionado:** Pendiente de creación.
---

### Interacción 20 – Sesiones auténticas y vigentes (4D)

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Emisión y revalidación de sesiones (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Me apoyé en Codex para distinguir sesiones emitidas después de autenticar y comprobar su vigencia sin exigir permisos administrativos a todos los usuarios.
* **Resumen de la respuesta recibida:** Codex inspeccionó el modelo de confianza e implementó autoridad privada por composición, principal inmutable y revalidación administrativa estricta, con revocación y pruebas concurrentes.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el alcance autorizado por Carlos: sesiones manuales rechazadas y constructores antiguos sin autoridad cerrados. Dejé navegación e integración comercial para sus responsables y la revisión independiente para Jesús. No se reconstruyen conversaciones o fechas sin evidencia; las posibles fechas adicionales de trabajo deben confirmarse antes del PR.
* **Verificación realizada:** Codex ejecutó la suite con Temurin 17.0.21+9 y objetivo Java 17. El verify final contabilizó 748 casos: 708 aprobados, 0 fallos, 0 errores y 40 integraciones PostgreSQL omitidas, incluidos 16 casos nuevos del validador. git diff --check pasó. No se conectó a PostgreSQL ni aplicó V104/V105. No se hizo commit o publicación.
* **Commit o Pull Request relacionado:** Pendiente de creación.
#### Continuación de la Interacción 20 – Auditoría y entrega de 4D

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Auditoría de sesiones (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Pedí auditar emisión, revocación y compatibilidad y preparar el diff para Carlos y Jesús.
* **Resumen de la respuesta recibida:** Codex revisó los doce archivos y reforzó pruebas de subclases hostiles, fábrica sobrescrita, revocación ajena y cierre directo durante la lectura, sin corregir producción.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el refuerzo de cobertura dentro del alcance y mantuve pendientes la revisión independiente y la integración comercial. No se reconstruyeron interacciones de otras fechas sin evidencia.
* **Verificación realizada:** mvn -o verify con Temurin 17.0.21+9: 751 casos, 711 aprobados, cero fallos y errores y 40 integraciones PostgreSQL omitidas. Codex comprobó git diff --check y preparó el diff íntegro fuera del repositorio para revisión. No hubo PostgreSQL, migraciones, commit ni push.
* **Commit o Pull Request relacionado:** Pendiente de creación.
---

### Interacción 21 – Publicación de 4D y preparación de entrega

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Fase 3 – Cierre verificable de identidad (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Autoricé publicar 4D y completar las verificaciones locales y documentación para preparar el PR hacia develop.
* **Resumen de la respuesta recibida:** Codex publicó 3e4afd7, midió PBKDF2 con fixtures sintéticos y explicitó compatibilidad y límites de la entrega independiente, sin conectar módulos comerciales.
* **Qué se acepta, modifica o descarta y por qué:** Acepté registrar los resultados reales y mantener pendientes la revisión de Jesús, inventario histórico, migraciones y futuras decisiones compartidas de integración. La autorización de trabajo autónomo no equivale a aprobar seguridad ni aplicar esquemas.
* **Verificación realizada:** mvn -o verify con Temurin 17.0.21+9 y objetivo Java 17: 751 casos, 711 aprobados, cero fallos/errores y 40 integraciones PostgreSQL omitidas. La medición secuencial usó un calentamiento y tres muestras por caso: medianas de 43,209 ms para el formato actual y 385,676 ms para un millón de iteraciones con resultado de 64 bytes; no es una garantía de capacidad. git diff --check pasó. No hubo PostgreSQL ni aplicación de V104/V105. No se reconstruyen fechas sin registros suficientes.
* **Commit o Pull Request relacionado:** 3e4afd788d9b43187dca9ea93e41636e5ff99b2d; commit documental y PR pendientes de creación al registrar esta entrada.

### Interacción 22 – Correcciones bloqueantes del PR #22

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Seguridad y compatibilidad de identidad (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Autoricé corregir la reutilización de cuentas y admitir hashes históricos válidos al actualizar metadatos.
* **Resumen de la respuesta recibida:** Codex incorporó UUID por inserción, comparación en sesiones y actualizaciones, V106 con backfill e inmutabilidad y un parser estructural compartido sin ejecutar PBKDF2 para validar formatos.
* **Qué se acepta, modifica o descarta y por qué:** Acepté la implementación y regresiones dentro de identidad; V106 queda preparada, sin aplicar, y su revisión y validación PostgreSQL siguen pendientes. No se integraron los cambios ajenos de develop.
* **Verificación realizada:** mvn -o verify con Temurin 17.0.21+9: 760 casos, 720 aprobados, 40 integraciones PostgreSQL omitidas, cero fallos y errores. Incluye reutilización de ID con ambos roles, revocación y sesiones hostiles, 82 pruebas JDBC simuladas y dos inspecciones estáticas de V106. Se comprobó que V106 no aparece en las 20 ramas remotas consultadas. No hubo SQL, conexión PostgreSQL ni ejecución de migraciones.
* **Commit o Pull Request relacionado:** PR #22; commits de corrección pendientes de creación al registrar esta entrada.

### Interacción 23 – Preparación de validación PostgreSQL aislada

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Validación pendiente de identidad (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Pedí preparar entorno, scripts y pruebas reproducibles de V104/V105/V106 sin ejecutar SQL.
* **Resumen de la respuesta recibida:** Codex preparó Compose sin puertos ni datos persistentes, Java en la misma red loopback, scripts de backfill/rollback y fallo por duplicados, captura de catálogos y tres integraciones nuevas opt-in. Documentó 19 casos Java propuestos y seis escenarios concurrentes de vendedor.
* **Qué se acepta, modifica o descarta y por qué:** Acepté preparar el laboratorio y mantener pendiente su ejecución específica. No se encontró Docker en PATH ni se instalaron herramientas; imágenes por digest, caché Linux, recursos y permiso de crear/desechar el laboratorio deben confirmarse con Carlos. Una aserción estática que confundía un comentario con tracing se corrigió, sin cambiar producción.
* **Verificación realizada:** mvn -o verify con Temurin 17.0.21+9: 768 casos, 725 aprobados, 43 PostgreSQL omitidos, cero fallos/errores; incluye cinco pruebas estáticas nuevas y tres integraciones nuevas omitidas. bash -n pasó sin ejecutar el runner. Compose y SQL solo se inspeccionaron estáticamente. No hubo conexiones, SQL ni migraciones; las entradas anteriores se conservan.
* **Commit o Pull Request relacionado:** PR #22; commit de preparación pendiente de creación al registrar esta entrada.

### Interacción 24 – Cierre verificable del PR #22 sin laboratorio

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Auditoría final de identidad (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Pedí completar lo verificable, publicar correcciones y consolidar el bloqueo de PostgreSQL sin improvisar un entorno.
* **Resumen de la respuesta recibida:** Codex revisó el PR, comentarios de Carlos, CI, migraciones y fixtures. Corrigió el fixture que podía ocultar fallos suprimidos de rollback/cierre y añadió tres regresiones con el coordinador real y driver simulado.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el refuerzo de pruebas y un único bloqueo operativo documentado: falta un laboratorio autorizado e identificado con equipo/VM, digests, caché offline y secreto temporal. No se instaló Docker ni se sustituyó el entorno. La aprobación estática de Carlos no se registra como ejecución PostgreSQL ni aprobación de merge.
* **Verificación realizada:** mvn -o verify con Temurin 17.0.21+9: 771 casos, 728 aprobados, 43 PostgreSQL omitidos, cero fallos/errores. git diff --check pasó. Se comprobó develop en db9530c y ausencia de solapamientos entrantes de identidad sin integrar ramas. No hubo SQL, conexiones, migraciones ni cambios de producción o módulos ajenos.
* **Commit o Pull Request relacionado:** PR #22; commit de cierre pendiente de creación al registrar esta entrada.

### Interacción 25 – Laboratorio PostgreSQL autorizado en Actions

* **Fecha:** 22/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Ejecución PostgreSQL del PR #22 (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Autoricé implementar y ejecutar un laboratorio efímero en GitHub Actions, sin bases externas ni secretos reales.
* **Resumen de la respuesta recibida:** Codex agregó un workflow independiente, orquestador con inspección de aislamiento y limpieza, seis casos concurrentes V105 y dos ciclos con recuperación. Resolvió digests oficiales y comprobó SHA-256 de sus manifiestos.
* **Qué se acepta, modifica o descarta y por qué:** Acepté usar Actions con imágenes fijadas y credenciales aleatorias del job. Se conserva el workflow compartido y no se instala Docker local. La ejecución real y sus resultados deben verificarse tras publicar; no se anticipa su éxito.
* **Verificación realizada:** Temurin 17.0.21+9, mvn -o verify local: 777 casos, 728 aprobados, 49 PostgreSQL omitidos, cero fallos/errores. bash -n pasó. No hubo SQL local; el resultado PostgreSQL sigue pendiente de Actions al registrar esta entrada.
* **Commit o Pull Request relacionado:** PR #22; commit de laboratorio pendiente de creación al registrar esta entrada.

### Interacción 26 – PostgreSQL real completado en Actions

* **Fecha:** 22/11/2026 (logs UTC del 23/11).
* **Herramienta:** Codex y GitHub Actions.
* **Fase, tarea y rama:** Validación real y cierre de evidencia del PR #22 (`feature/daniel-identity-users`).
* **Objetivo de la consulta:** Ejecutar el laboratorio temporal autorizado, resolver fallos y registrar resultados verificables.
* **Resumen de la respuesta recibida:** Codex corrigió el contexto runner del workflow y una opción no admitida de Compose. Las ejecuciones 45112354069 (PR) y 45112346376 (rama) completaron dos ciclos limpios de 25 pruebas cada una: 19 integraciones y seis concurrencias V105, sin omisiones, fallos ni errores. También aprobaron SQL, reaplicaciones negativas y recuperación.
* **Qué se acepta, modifica o descarta y por qué:** Acepté la evidencia del laboratorio aislado y destruido; se resuelve el bloqueo de disponibilidad para esta validación. No equivale a desplegar migraciones en bases compartidas ni aprobar el merge. Se conservaron módulos ajenos y el job Maven compartido.
* **Verificación realizada:** PostgreSQL 18.7, Temurin 17.0.20.1+1 y Maven 3.9.16 en imágenes oficiales por digest verificado. V001/V104/V105/V106 aplicadas solo en tmpfs del runner. Artefacto 14789887647 descargado: SHA-256 28d020a1a11641191c895e94044b21ec2c16c448fe068e67b1dc67c086d12e03, coincidente con GitHub; summary completed/destroyed=true. Se comprobaron XML, inspecciones y SQLSTATE esperados. Local: 728 aprobadas y 49 PostgreSQL omitidas con Temurin 17.0.21+9; no hubo PostgreSQL local. git diff --check pasó.
* **Commit o Pull Request relacionado:** ca85e4f, 59fb4f7 y ccb995a; PR #22; commit documental de resultados pendiente de creación al registrar esta entrada.
## Complemento retrospectivo verificado — auditoría del 23/11/2026

Las interacciones 1–26 se conservan íntegras, incluido su contexto histórico.
Las entradas siguientes complementan omisiones, sin sustituir registros previos
ni contar dos veces una misma ejecución de pruebas. Se ordenan por la fecha de
la conversación recuperada; su numeración corresponde al registro retrospectivo.
Las horas se expresan en America/Bogota (UTC−05:00), convertidas desde los
timestamps de cada turno, no desde la fecha del commit.

Fuentes primarias consultadas: historial Codex **Analiza el proyecto la-exclusiva-p3**,
chat `01a1e55d-53e3-75e0-b8a7-e76d68db207c` (162 turnos recuperados, incluida
esta auditoría), y ChatGPT **Contexto del proyecto saved**, conversación
`6afb38ee-65f4-83ea-8a9d-a2307c1e68a9` (cinco intercambios recientes disponibles).
Los identificadores de turno se indican como evidencia, sin reproducir conversaciones
privadas completas o prompts literales. Git, los PR y CI sirven para contrastar
los resultados; no se usan por sí solos para atribuir uso de IA.

### Interacción 27 (retrospectiva) – Diagnóstico inicial del proyecto

* **Fecha:** 16/11/2026, 12:17.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Diagnóstico previo a JavaFX; `feature/sales-jdbc`, HEAD `f378c41`.
* **Objetivo de la consulta:** Pedí analizar estructura, autenticación, permisos, pruebas y dependencias sin modificar archivos.
* **Resumen de la respuesta recibida:** Codex distinguió la aplicación en memoria de los adaptadores JDBC y señaló que el menú JavaFX todavía no tenía acciones conectadas.
* **Qué se acepta, modifica o descarta y por qué:** Se mantuvo la inspección de solo lectura como base para delimitar mi trabajo; no se implementaron recomendaciones en ese turno.
* **Verificación realizada:** Turno Codex `01a1e55d-58de-7171-8d01-4d8b82ad69af`, solicitud y respuesta recuperadas. Inspección de código e informes existentes; no ejecutó Maven, JavaFX ni PostgreSQL. Sin archivos modificados.
* **Commit o Pull Request relacionado:** `f378c41`, referencia del diagnóstico; no se creó commit por esta consulta.

### Interacción 28 (retrospectiva) – Revisión cruzada de deuda de clientes

* **Fecha:** 16/11/2026, 14:17.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión del PR #12, desde `feature/javafx-login-navigation`.
* **Objetivo de la consulta:** Pedí revisar el cálculo JDBC de deuda y su compatibilidad con clientes y Cartera.
* **Resumen de la respuesta recibida:** Codex comprobó la agregación separada de líneas y pagos y recomendó no confundir la deuda de ventas con el saldo general ni usar esa consulta como autorización de escritura.
* **Qué se acepta, modifica o descarta y por qué:** La recomendación quedó como revisión informativa; no se modificó el módulo del compañero ni se publicó una aprobación automática.
* **Verificación realizada:** Turno `01a1e5ca-d407-74a2-a617-a4aad143a475`: siete archivos y cuatro commits revisados en `aa443c0`. Sin cambios de archivos; evidencia de inspección, no de ejecución PostgreSQL.
* **Commit o Pull Request relacionado:** [PR #12](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/12).

### Interacción 29 (retrospectiva) – Riesgo de snapshots antiguos en clientes

* **Fecha:** 17/11/2026, 21:51.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión cruzada del PR #14; `feature/javafx-login-navigation`.
* **Objetivo de la consulta:** Pedí comprobar si una actualización de cliente podía sobrescribir un saldo ajustado concurrentemente.
* **Resumen de la respuesta recibida:** Codex identificó el riesgo al combinar snapshots antiguos con `CustomerRepositoryJdbc.update` y recomendó proteger la escritura y añadir una regresión antes del merge.
* **Qué se acepta, modifica o descarta y por qué:** Se conservaron los límites de revisión; no se corrigió código ajeno ni se confundió una recomendación con aprobación formal.
* **Verificación realizada:** Turno `01a1ec91-13a3-7340-8e9d-f18b553f21a6`: nueve archivos y cinco commits en `5d9a0a8`. No ejecutó pruebas locales ni operaciones PostgreSQL; sin archivos modificados.
* **Commit o Pull Request relacionado:** [PR #14](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/14).

### Interacción 30 (retrospectiva) – Diagnóstico y corrección de CI al cerrar sesión

* **Fecha:** 17/11/2026, 22:52–23:03.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Complemento de la Interacción 8; `feature/javafx-navigation-ux`, PR #17.
* **Objetivo de la consulta:** Autoricé investigar el fallo de restauración del Login después de logout y publicar la corrección verificada.
* **Resumen de la respuesta recibida:** Codex diagnosticó una carrera de maximización/restauración y añadió `primaryStage.hide()` en `App.java`, sin debilitar las aserciones de permisos.
* **Qué se acepta, modifica o descarta y por qué:** Acepté el ajuste mínimo y mantuve explícita la diferencia entre la prueba Windows y su confirmación posterior en CI Linux.
* **Verificación realizada:** Turnos `01a1ecc8-8250-70d1-ac1b-ddddec607c3e` y `01a1ecd3-10ec-7d70-8c3b-19214b0960d9`. Local: JDK 25.0.2, 186 aprobadas y dos omitidas. CI posterior Java 17: 196 aprobadas y tres omitidas, cero fallos/errores. Archivos afectados: `App.java` y esta bitácora.
* **Commit o Pull Request relacionado:** `e8f546b`, `2c489a3`; [ejecución 44234848131](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/44234848131), PR #17. No son pruebas ejecutadas de nuevo durante esta auditoría.

### Interacción 31 (retrospectiva) – Simplificación académica de la bitácora

* **Fecha:** 17/11/2026, 23:39–23:55.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión documental; `feature/javafx-navigation-ux` y después `docs/simplify-developer2-activity5-log`.
* **Objetivo de la consulta:** Pedí resumir la Interacción 8 sin inventar pruebas ni alterar las interacciones 1–7.
* **Resumen de la respuesta recibida:** Codex propuso un texto breve, esperó su aprobación y preparó su incorporación documental a develop mediante un PR separado.
* **Qué se acepta, modifica o descarta y por qué:** Aprobé el resumen y su publicación, conservando la evidencia técnica sin repetir el relato completo de cada paso.
* **Verificación realizada:** Turnos `01a1ecf3-a8f4-7492-8302-7567ea821403` y `01a1ed02-447a-7662-9931-336bccb55c70`; diff exclusivamente de `developer2-ai-log.md`, entradas 1–7 intactas y CI del PR #19 satisfactoria según el registro recuperado.
* **Commit o Pull Request relacionado:** `d8f1997`; [PR #19](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/19).

### Interacción 32 (retrospectiva) – Revisión de integración y flujos protegidos

* **Fecha:** 18/11/2026, 08:31, 18:19 y 19:26.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión cruzada de `feature/full-jdbc-integration` y `feature/carlos-finance-close`.
* **Objetivo de la consulta:** Pedí revisar los PR #18 y #20 y después las correcciones del líder, sin modificar sus módulos.
* **Resumen de la respuesta recibida:** Codex señaló problemas del flujo trabajador-vendedor, resultados inciertos y confirmaciones de Cartera. En la segunda revisión del PR #20 consideró atendidas las tres observaciones examinadas.
* **Qué se acepta, modifica o descarta y por qué:** Se diferenciaron hallazgos iniciales, correcciones posteriores y riesgos operativos; la recomendación no sustituyó la revisión humana.
* **Verificación realizada:** Turnos `01a1eedb-5c5c-7bc1-9d99-cd36a1485fe6`, `01a1f0f5-9940-79b0-9cda-12a28c2eafec` y `01a1f132-2f80-7033-bbf6-7146929af5b8`. Revisión estática de diffs y registros de GitHub; sin archivos modificados ni pruebas locales, JavaFX o PostgreSQL ejecutados en esas revisiones.
* **Commit o Pull Request relacionado:** [PR #18](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/18) y [PR #20](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/20); último HEAD revisado `8f6801e`.

### Interacción 33 (retrospectiva) – Integración autorizada de develop en identidad

* **Fecha:** 18/11/2026, 22:19–22:25.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Compatibilidad y regresiones; `feature/daniel-identity-users`.
* **Objetivo de la consulta:** Pedí inspeccionar y después autoricé integrar develop mediante merge normal, preservando los cuatro commits propios y los cambios financieros.
* **Resumen de la respuesta recibida:** Codex comparó las ramas, realizó el merge sin conflictos y separó pruebas dirigidas de la suite general y de las integraciones omitidas.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé la integración y su publicación posterior; no se resolvieron conflictos automáticamente ni se consideró aprobado el contrato pendiente.
* **Verificación realizada:** Turnos `01a1f1d0-ac41-7cd1-b352-b6200eae50c7`, `01a1f1d3-08f7-72a1-a25f-1d1b333caf91` y `01a1f1d6-d0dd-7e32-8494-e07f4a46f6ee`. JDK 25.0.2, objetivo Java 17: grupos dirigidos de 58 y 34 aprobadas; suite de 286 aprobadas y 29 PostgreSQL omitidas, cero fallos/errores. Sin ediciones manuales de identidad, contratos o bitácora, ni SQL.
* **Commit o Pull Request relacionado:** Merge `00a759c` desde `c5a95af`; posteriormente conservado en el PR #22.

### Interacción 34 (retrospectiva) – Corrección de la barrera de seguridad V104

* **Fecha:** 19/11/2026, 00:47–00:55.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Complemento de la Interacción 17; `feature/daniel-identity-users`.
* **Objetivo de la consulta:** Pedí revisar antes del commit y corregir la comprobación insuficiente de CHECK, trigger y función, sin ejecutar SQL.
* **Resumen de la respuesta recibida:** Codex rechazó CHECK triviales, triggers con WHEN/UPDATE OF o eventos incompletos y funciones incompatibles; comparó definición y atributos, no solo nombres.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé corregir únicamente las dos clases de integración y seguridad; no se modificó V104 ni se presentó la simulación como validación real.
* **Verificación realizada:** Turnos `01a1f258-2a19-76b0-8b28-285891a6f278`, `01a1f259-b261-7bd2-aea9-6d5f37b017bf` y `01a1f260-190e-7780-9536-959200fbda39`. Java 17: 54 casos simulados y 83 regresiones, **137 aprobados**, cero fallos/errores/omisiones. Complementa los 104 casos iniciales ya registrados; no se suman ambos totales. Archivos: `UserSecurityVersionPostgresIntegrationTest.java` y `UserSecurityVersionIntegrationSafetyTest.java`.
* **Commit o Pull Request relacionado:** `1d5eda0`, incorporado posteriormente al PR #22.

### Interacción 35 (retrospectiva) – Diseño previo de exclusividad de vendedor

* **Fecha:** 19/11/2026, 19:40.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Propuesta PostgreSQL previa a 4A; `feature/daniel-identity-users`.
* **Objetivo de la consulta:** Pedí evaluar una garantía de exclusividad frente a escrituras externas sin crear ni aplicar una migración.
* **Resumen de la respuesta recibida:** Codex propuso el índice único parcial de vendedor activo no nulo y diagnóstico de duplicados; explicó las diferencias entre commit, rollback y creación concurrente del índice.
* **Qué se acepta, modifica o descarta y por qué:** Se mantuvo como propuesta para coordinación; no se corrigieron duplicados ni se modificó V104. La implementación posterior está registrada por separado en 4A.
* **Verificación realizada:** Turno `01a1f665-74d7-7991-acb6-c8cc3e3bed79`: inspección del esquema, excepciones JDBC y migraciones con HEAD `86fae23`; sin archivos modificados, pruebas, conexiones ni ejecución SQL.
* **Commit o Pull Request relacionado:** Antecedente de `ad3966f` y V105 en el PR #22; el commit no demuestra por sí solo esta conversación.

### Interacción 36 (retrospectiva) – Límites documentales de PBKDF2 antes del commit

* **Fecha:** 22/11/2026, 19:52–19:56.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Complemento de 4C; `feature/daniel-identity-users`.
* **Objetivo de la consulta:** Pedí comprobar que los riesgos operativos e históricos estuvieran documentados antes de crear el commit autorizado.
* **Resumen de la respuesta recibida:** Codex se detuvo al faltar la declaración de inventario histórico incompleto y la medición acotada pendiente. Tras la autorización documental añadió ambas aclaraciones en `docs/password-verification.md` y creó el commit.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé la corrección documental mínima; no se cambiaron otra vez producción, pruebas ni bitácora en ese paso.
* **Verificación realizada:** Turnos `01a205e3-8f6b-7c13-be41-84f21ca2301a` y `01a205e7-d262-7121-a30b-c4c36a563a4b`. Diff y staging revisados; se confirmaron los informes previos de 404 aprobadas, **sin repetir pruebas**. No hubo SQL ni push durante la creación local.
* **Commit o Pull Request relacionado:** `af35bbf`, incorporado al PR #22. La medición posterior está en la Interacción 21.

### Interacción 37 (retrospectiva) – Apoyo de ChatGPT para delimitar el laboratorio

* **Fecha:** 22/11/2026, 23:14–23:18.
* **Herramienta:** ChatGPT.
* **Fase, tarea y rama:** Coordinación del laboratorio para `feature/daniel-identity-users`, PR #22.
* **Objetivo de la consulta:** Compartí el informe de bloqueo de Codex y después la autorización de Carlos para priorizar GitHub Actions.
* **Resumen de la respuesta recibida:** ChatGPT distinguió código preparado de PostgreSQL validado y propuso concentrar implementación, ejecución y corrección del laboratorio en una tarea para Codex.
* **Qué se acepta, modifica o descarta y por qué:** Retomé la tarea integral con Codex; la orientación no se tomó como prueba de que el entorno ya estuviera funcionando ni como autorización de merge.
* **Verificación realizada:** Turnos ChatGPT `ce2ecc5b-269c-43d4-ace5-070a24e269ed` y `3ee72060-f996-44fd-ad78-41becd5b3c22`, con solicitud y respuesta fechadas. ChatGPT no editó archivos ni ejecutó pruebas; la ejecución posterior corresponde a Codex y Actions, registrada en 25–26.
* **Commit o Pull Request relacionado:** PR #22; `19013b6` era el informe compartido, no un commit generado por ChatGPT.

### Interacción 38 (retrospectiva) – Revisión conversacional de la entrega PostgreSQL

* **Fecha:** 22/11/2026, 23:44.
* **Herramienta:** ChatGPT.
* **Fase, tarea y rama:** Preparación de revisión final del PR #22; `feature/daniel-identity-users`.
* **Objetivo de la consulta:** Compartí el resultado final de Codex, las migraciones y los enlaces de Actions para preparar la revisión del equipo.
* **Resumen de la respuesta recibida:** ChatGPT resumió las 50 pruebas reportadas por Codex y redactó una propuesta de mensaje para solicitar la revisión final, sin afirmar haber ejecutado esas pruebas.
* **Qué se acepta, modifica o descarta y por qué:** Se mantuvo la distinción entre evidencia técnica, propuesta de comunicación y aprobación humana. No se atribuye a ChatGPT el envío del mensaje ni la ejecución del laboratorio.
* **Verificación realizada:** Turno `8cc490aa-d919-43ee-bfd1-9ab4fdf004ee`. El enlace compartido corresponde a [Actions 45113153620](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45113153620); archivos mencionados: `docs/identity-postgres-results.md`, sin modificaciones por ChatGPT. Las pruebas no se suman a la Interacción 26.
* **Commit o Pull Request relacionado:** PR #22; informe de `ff995ec` ya publicado por Codex.

### Interacción 39 (retrospectiva) – Cierre tras el merge y solicitud de auditoría

* **Fecha:** 22/11/2026, 23:55–23:56.
* **Herramienta:** ChatGPT.
* **Fase, tarea y rama:** Cierre del PR #22 y planificación de la auditoría documental desde develop.
* **Objetivo de la consulta:** Informé que el líder aprobó el PR y que ya hice merge; después señalé interacciones faltantes en la bitácora.
* **Resumen de la respuesta recibida:** ChatGPT recomendó no seguir modificando la rama fusionada y auditar las fuentes con Codex, sin inventar fechas ni conversaciones inaccesibles.
* **Qué se acepta, modifica o descarta y por qué:** Inicié esta auditoría independiente; no se presupuso acceso completo a ChatGPT ni que una recomendación de IA equivaliera a aprobación del líder.
* **Verificación realizada:** Turnos `1ee36c3a-4085-4295-bf78-decf11e77ee8` y `73babd1a-2a11-42d7-b262-25f8fa3bef06`. ChatGPT no editó archivos. En esta auditoría GitHub confirmó aprobación de Carlos a las 23:51 y merge a las 23:53 del 22/11 en America/Bogota.
* **Commit o Pull Request relacionado:** [PR #22 fusionado](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/22), merge `83058a6`.

### Interacción 40 – Auditoría y recuperación de la bitácora

* **Fecha:** 23/11/2026; solicitud iniciada el 22/11 a las 23:56, con continuación después de medianoche.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Auditoría documental; `codex/complete-developer2-ai-log`, desde `origin/develop` en `83058a6`.
* **Objetivo de la consulta:** Pedí recuperar solo interacciones verificables, conservar las entradas anteriores y preparar commit y PR independientes.
* **Resumen de la respuesta recibida:** Codex contrastó historial conversacional, Git, PR y convenciones; añadió este complemento cronológico y separó los registros pendientes de evidencia.
* **Qué se acepta, modifica o descarta y por qué:** Se conservó íntegro el texto anterior y se excluyeron otras conversaciones sin vínculo inequívoco con La Exclusiva. Git se usó para contrastar cambios, no para deducir uso de IA.
* **Verificación realizada:** Turno Codex `01a206c3-534a-70a1-b0bb-b833b6a69a3e`. Se comprobaron las convenciones de `docs/ai-usage/README.md`, ocho campos por entrada, UTF-8, fechas, diff exclusivamente documental y conservación del prefijo anterior byte por byte. No se ejecutó Maven manualmente, SQL ni migraciones; la CI posterior se informa en el PR independiente.
* **Commit o Pull Request relacionado:** PR documental independiente pendiente de creación al registrar esta entrada; el PR #22 no se modifica.

### Fuentes que siguen pendientes de aportar

- **ChatGPT anterior al 22/11/2026 a las 23:14:** el lector conectado devolvió solo cinco intercambios recientes de *Contexto del proyecto saved*, sin cursor para recuperar los anteriores. Si hubo asesoría sobre contratos, seguridad o actividades previas, hace falta la exportación o los mensajes con sus fechas. La fecha de creación del chat no permite reconstruirlos.
- **Gemini, 26 y 29/10/2026:** no se consultaron las conversaciones originales de las interacciones 1–3. Se preservan sus registros existentes, sin ampliarlos; para contrastarlos hace falta aportar la fuente original.
- **Solicitud V105 del 19/11/2026 a las 19:51:** turno Codex `01a1f66f-917b-7000-8c7b-5df334358b7d`, con un adjunto de instrucciones, pero sin respuesta final recuperada. Se requiere el registro completo para atribuir un resultado; no se convirtió en una implementación presunta.
- **20 y 21/11/2026 u otras conversaciones externas:** no aparecieron interacciones fechadas de esos días en las fuentes consultadas. No se presume que existieran por continuidad del desarrollo. Si hubo consultas, deben aportarse los mensajes y timestamps; no se crearán entradas usando solo commits o recuerdos.

### Interacción 41 – Modernización visual independiente y coordinación

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex; se ensayaron dos ediciones con ImageGen, descartadas por alterar detalles del original.
* **Fase, tarea y rama:** Interfaz JavaFX e identidad visual; `feature/ui-modernization`, desde develop `cfdc181`.
* **Objetivo de la consulta:** Implementar el dashboard aprobado, modernizar pantallas y preparar identidad de escritorio sin tocar los archivos reservados a Carlos.
* **Resumen de la respuesta recibida:** Se creó un tema central, adaptación visual de ocho FXML y un dashboard alternativo con navegación existente. La inspección remota detectó el FXML principal ocupado por Carlos y se dejó la integración documentada.
* **Qué se acepta, modifica o descarta y por qué:** Tras reiterar el delantal azul y la integración sin fondo, el usuario eligió esperar al original azul transparente; se descartaron ambas ediciones generadas y la captura roja no se integra. No se inventan cifras, logo independiente ni iconos. Dashboard principal, analíticas e iconos quedan delimitados para coordinación y recursos originales.
* **Verificación realizada:** Temurin 17.0.21+9 y Maven offline verify: 805 pruebas, 750 aprobadas, 55 PostgreSQL omitidas, 0 fallos/errores. Tres pruebas visuales nuevas cargan FXML/CSS, navegan con servicios memory y verifican redistribución. Capturas JavaFX reales en `docs/ui-review`; ventana de revisión visible en memoria. Sin SQL, migraciones ni cambios de controladores/servicios. Evidencia y limitaciones en `docs/ui-modernization.md`.
* **Commit o Pull Request relacionado:** Commit y PR de modernización visual pendientes al registrar esta entrada; sin modificación del PR #22 fusionado.

### Interacción 42 – Correcciones de revisión del PR visual #25

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión de las cuatro observaciones de Carlos; `feature/ui-modernization`.
* **Objetivo de la consulta:** Integrar develop/PR #24 y corregir compatibilidad del dashboard, contraste del login, permisos y ventanas compactas, manteniendo el PR como Draft.
* **Resumen de la respuesta recibida:** Se realizó merge normal sin conflictos (`4766324`); se recuperó la observación completa del PR y el usuario la confirmó. Se adaptaron ambos dashboard FXML a administración real, sesiones y logout; se añadieron enlaces visuales de permisos, aviso de login contrastado y desplazamiento en Login/Cartera/Inventario.
* **Qué se acepta, modifica o descarta y por qué:** Se mantuvieron intactos App.java, controladores y servicios; el rojo inline se conserva con un fondo CSS que garantiza contraste. No se inventaron recursos de marca: solo se reservaron sus rutas. Se ajustó Scene/CSS/layout en pruebas existentes tras detectar errores de lookup previos a la creación del skin, sin retirar aserciones.
* **Verificación realizada:** Temurin 17.0.21+9, Maven offline `clean verify`: 828 casos, 769 aprobados, 59 PostgreSQL omitidos, 0 fallos/errores; nueve pruebas visuales aprobadas. Se comprobaron permisos de ambos roles, administración, rechazo de sesión sintética/revocada, logout con recuperación, tres mensajes de login con contraste >= 4.5:1 y desplazamiento compacto. Se renovaron capturas JavaFX reales. Las primeras suites fallidas y la corrección del montaje visual se documentan en `docs/ui-modernization.md`. Sin SQL, migraciones ni revisión manual fingida. Entradas anteriores preservadas byte por byte.
* **Commit o Pull Request relacionado:** Continuación del [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25), que permanece en Draft; commit de corrección pendiente al registrar esta entrada.

### Interacción 43 – Integración de la mascota original aprobada

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex; inspección local de píxeles/alfa con System.Drawing, sin generar ni reconstruir imágenes con IA.
* **Fase, tarea y rama:** Identidad visual del dashboard; `feature/ui-modernization`, continuación del PR #25 desde `eceb23f`.
* **Objetivo de la consulta:** Integrar el PNG original aprobado de la mascota, comprobar transparencia y bordes, conservar las funcionalidades del PR #24 y preparar la coordinación de logo/iconos.
* **Resumen de la respuesta recibida:** Se copió exactamente `Botella Real La Exclusiva.png` al classpath y se integró con ImageView sobre el degradado del banner en ambos FXML. Se comprobó el alfa sobre fondos azul/blanco/negro; no se observaron residuos visibles que justificaran retocar el archivo. Se preparó documentación concreta de Stage, launcher y accesos directos sin modificar App.java.
* **Qué se acepta, modifica o descarta y por qué:** Se conserva el adjunto byte por byte, incluidos rostro, corona, sombras, delantal y logo; no se recorta ni se extrae el logo del delantal. El banner distribuye texto e imagen sin solaparlos. El logo independiente y sus variantes PNG/ICO siguen pendientes; no se inventan recursos ni se declara comprobada la barra de tareas.
* **Verificación realizada:** Temurin 17.0.21+9, Maven offline `clean verify`: 830 casos, 771 aprobados, 59 PostgreSQL omitidos, 0 fallos/errores. Once pruebas visuales, dos adicionales de integridad/alfa y adaptación a 1280/800/700 px. SHA-256 del adjunto, recurso y copia dentro del JAR coincidentes; capturas JavaFX reales actualizadas. La procedencia y los resultados se documentan en el README de la mascota y `docs/ui-modernization.md`. Sin SQL, migraciones, cambios de controladores o servicios; entradas 1–42 preservadas byte por byte.
* **Commit o Pull Request relacionado:** Continuación del [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25), que permanece en Draft; commit de integración pendiente al registrar esta entrada.

### Interacción 44 – Legibilidad y ventanas compactas sin interferir con Compras

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Mejoras visuales justificadas del PR #25; `feature/ui-modernization` desde `8be2051`.
* **Objetivo de la consulta:** Revisar CSS, textos, formularios y tablas en ventanas pequeñas, conservando identidad visual, navegación y permisos, sin integrar el PR #26 pendiente.
* **Resumen de la respuesta recibida:** Se comprobó que #26 seguía abierto y se revisó su patch de Cartera. Se ampliaron cuatro campos y el selector de envase de Inventario, se fijó altura preferida de 200 px para sus tablas y se aplicó un ancho CSS específico a la descripción de Cartera. Se verificaron navegación/logout a 800×600 y formularios a 560×600.
* **Qué se acepta, modifica o descarta y por qué:** Se retiraron los ensayos sobre el FXML de Cartera al detectar solapamiento con #26. También se descartó el ajuste multilínea porque recortaba filas; no se incorporaron controladores ni renderizadores nuevos. Se conserva la mascota original, el diseño aprobado y la navegación actual, sin Compras provisional ni cambios de App.java, servicios, base de datos o iconos.
* **Verificación realizada:** Temurin 17.0.21+9, Maven offline `clean verify`: 833 casos, 774 aprobados, 59 PostgreSQL omitidos, 0 fallos/errores. Las 14 pruebas visuales se repitieron al añadir una captura centrada en el formulario de Cartera: 14/14. Las pruebas detectaron y corrigieron cuatro ayudas recortadas; comprueban espacio de los tres tipos de envase, límites de acciones, navegación completa y desplazamiento horizontal del inventario memory. Capturas reales en `docs/ui-review`. Límites y coordinación tras #26 en `docs/ui-modernization.md`; sin SQL ni integración de esa rama. Entradas anteriores conservadas byte por byte.
* **Commit o Pull Request relacionado:** Continuación del [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25), que sigue en Draft; commit pendiente al registrar esta entrada.

### Continuación de la Interacción 44 – CI multiplataforma y cambio de base

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Validación posterior al push `7100748`; `feature/ui-modernization`.
* **Objetivo de la consulta:** Completar la comprobación de CI y del estado real del PR #25 dentro de la misma tarea, sin resolver archivos compartidos sin coordinación.
* **Resumen de la respuesta recibida:** La CI de Ubuntu/Java 17 detectó ayudas recortadas en ID del producto y precio de media canasta. Se ampliaron únicamente esos anchos a 155/185 px. Durante la tarea Carlos fusionó #26 en `6e6db09` a las 15:12:38 de America/Bogota; se actualizó la referencia de develop y se inspeccionó el solapamiento con merge-tree, sin integrar cambios.
* **Qué se acepta, modifica o descarta y por qué:** Se mantienen estrictas las pruebas de métricas y se corrige el tamaño, en lugar de aceptar texto recortado. No se resuelven automáticamente los conflictos de main_menu_view.fxml, portfolio_view.fxml y AuthenticationServiceTest.java: requieren coordinar conservación de Compras, Cartera y regresiones de sesión con Carlos.
* **Verificación realizada:** CI fallida inicial: run `45257242567`, 833 casos, 1 error agregado de métricas y 59 omitidos. Tras la corrección, Temurin 17.0.21+9 y Maven offline clean verify local: 833 casos, 774 aprobados, 59 PostgreSQL omitidos, 0 fallos/errores, incluidas 14 visuales. Capturas de Inventario renovadas. No hubo SQL ni merge; la CI posterior se comprobará tras publicar. Entradas anteriores preservadas byte por byte.
* **Commit o Pull Request relacionado:** [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25) en Draft; corrección posterior a `7100748` pendiente de commit al registrar esta continuación.

### Interacción 45 – Merge autorizado de Compras y Cartera en el dashboard moderno

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Integración del PR #26 en el PR #25; `feature/ui-modernization` desde `bc90f12`.
* **Objetivo de la consulta:** Fusionar origin/develop mediante merge normal, resolver tres conflictos sin perder diseño, Compras, Cartera, permisos ni logout y verificar los tres tamaños solicitados.
* **Resumen de la respuesta recibida:** Se incorporó develop `6e6db09`, conservando MainController y servicios de Carlos. Se combinaron los dos FXML del menú moderno con btnPurchases y acceso rápido sujeto al botón real; Cartera conserva ScrollPane y tabla de 150 px. Se combinaron las regresiones de autenticación y se añadieron dos pruebas de recepción/abonos con fixtures memory y capturas reales a 1280×800, 1024×768 y 800×600.
* **Qué se acepta, modifica o descarta y por qué:** Se preservan ambos historiales y los commits 7100748/bc90f12. Se compacta solo el espaciado lateral para incluir Compras sin ocultar logout; ambos menús quedan idénticos. No se reescribe lógica, permisos ni la mascota original; no se inventan métricas. App.java, launcher e iconos quedan intactos. Las modificaciones funcionales heredadas son las de develop, no cambios propios adicionales.
* **Verificación realizada:** La ejecución dirigida inicial dio 73 casos, 1 error de selector de bienvenida tras abandonar dashboard; las 16 visuales pasaron. Se corrigió la ubicación de esa aserción sin eliminarla. Suite final Temurin 17.0.21+9, Maven offline clean verify: 839 casos, 779 aprobados, 60 PostgreSQL omitidos, 0 fallos/errores. Se comprobaron recepción con movimiento de stock, abonos sin modificar existencias, roles, administración, login/logout y recuperación; capturas inspeccionadas. Sin SQL ni migraciones. Entradas anteriores preservadas byte por byte.
* **Commit o Pull Request relacionado:** [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25), permanece Draft; merge y CI pendientes de publicación al registrar esta entrada.
### Continuación de la Interacción 45 – Ancho de navegación en Ubuntu

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Comprobación de CI del merge 9d7dd84; `feature/ui-modernization`.
* **Objetivo de la consulta:** Completar la integración y verificar la CI del commit publicado, conservando las pruebas estrictas de legibilidad.
* **Resumen de la respuesta recibida:** Las ejecuciones 45261680900/45261691421 detectaron recorte de «Compras y Recepción» con la fuente de Ubuntu. Se amplió únicamente el lateral a 256 px en ambos menús y se renovaron capturas reales de las escenas integradas.
* **Qué se acepta, modifica o descarta y por qué:** Se corrige el ancho sin debilitar la aserción ni alterar permisos, servicios, controladores o mascota. El merge publicado conserva ambos padres y GitHub confirmó ausencia de conflictos; el PR permanece Draft.
* **Verificación realizada:** CI inicial: 839 casos, 1 error, 60 omitidos. Repetición local final con Temurin 17.0.21+9, Maven offline clean verify: 839 casos, 779 aprobados, 60 PostgreSQL omitidos, 0 fallos/errores, incluidas 16 pruebas visuales. Captura a 800×600 inspeccionada tras ampliar el lateral. La duración incluyó una pausa prolongada del equipo y no es una medición de rendimiento. Sin SQL ni migraciones; entradas previas intactas. CI de la corrección pendiente de publicación al registrar esta continuación.
* **Commit o Pull Request relacionado:** [PR #25](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/pull/25), sigue Draft; corrección posterior al merge 9d7dd84.
### Interacción 46 – Accesibilidad y revisión visual independiente de Alertas

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Revisión visual del PR #25; feature/ui-modernization desde a0c7126.
* **Objetivo de la consulta:** Revisar contraste, teclado, legibilidad y ventanas compactas conservando funcionalidades, mascota y permisos, sin integrar el PR #27.
* **Resumen de la respuesta recibida:** Se confirmó árbol limpio y PR #27 abierto. Las pruebas detectaron contraste insuficiente del extremo claro del banner y falta de nombres accesibles explícitos en el login. Se corrigieron solo CSS y FXML: banner con contraste mínimo 5,08:1, borde de foco azul profundo sobre tarjetas claras y nombres Usuario/Contraseña. Se amplió a Compras la prueba de formularios 560×600.
* **Qué se acepta, modifica o descarta y por qué:** Se conservan los controles, eventos, sesiones, permisos, servicios y mascota original. No se aplican ajustes cosméticos a formularios que ya pasan las comprobaciones. Tab/MAYÚS+Tab conserva el orden y el botón predeterminado. El foco y estado deshabilitado se verifican sin modificar controladores. No se integra Alertas ni se inventa logo o información comercial.
* **Verificación realizada:** Primera dirigida: 18 casos, dos errores agregados por contraste/nombres accesibles; repetición 18/18. Suite final con Temurin 17.0.21+9 y Maven offline clean verify: 841 casos, 781 aprobados, 60 PostgreSQL omitidos, 0 fallos/errores, incluidas 18 visuales. Capturas JavaFX reales inspeccionadas y documentadas; recorridos heredados a 1280×800, 1024×768 y 800×600 conservados. Sin SQL, migraciones o cambios Java de producción. Entradas anteriores preservadas byte por byte.
* **Commit o Pull Request relacionado:** PR #25 en Draft; commit y CI pendientes de publicación al registrar esta entrada.
### Continuación de la Interacción 46 – Ayuda tributaria legible en Ubuntu

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Validación de GitHub Actions del commit f278ee1; feature/ui-modernization.
* **Objetivo de la consulta:** Completar la revisión multiplataforma y corregir únicamente defectos demostrados antes de cerrar la entrega.
* **Resumen de la respuesta recibida:** La CI 45291073097 detectó recorte de la ayuda de supplierTaxId en el formulario compacto de Compras. Se añadió un ancho CSS preferido de 210 px y se renovaron cinco capturas de Compras, sin tocar su FXML, controlador o servicio.
* **Qué se acepta, modifica o descarta y por qué:** Se conserva la aserción estricta de legibilidad y se permite que el FlowPane redistribuya sus campos. No se integra el PR #27, no se altera la mascota ni los contratos funcionales.
* **Verificación realizada:** CI inicial: 841 casos, 1 error, 60 omitidos. Repetición local con Temurin 17.0.21+9 y Maven offline clean verify: 841 casos, 781 aprobados, 60 PostgreSQL omitidos, 0 fallos/errores; 18 visuales aprobadas. Captura a 560×600 inspeccionada tras ampliar el campo. git diff --check correcto; sin SQL ni migraciones. Entradas anteriores conservadas byte por byte. CI de la corrección pendiente de publicación al registrar esta continuación.
* **Commit o Pull Request relacionado:** PR #25 en Draft; corrección posterior a f278ee1.
### Interacción 47 – Alineación derecha de Exclusito y revisión local

* **Fecha:** 23/11/2026.
* **Herramienta:** Codex.
* **Fase, tarea y rama:** Ajuste tras revisión personal del usuario; feature/ui-modernization desde d7780b6.
* **Objetivo de la consulta:** Alinear la mascota al extremo derecho del banner, mantener texto a la izquierda y adaptar tamaños; evaluar el recurso necesario para el gesto de sorbo y abrir la aplicación, sin publicar antes de aprobación visual.
* **Resumen de la respuesta recibida:** Se sustituyó solo el contenedor visual del banner por HBox en ambos menús, con texto expansible e ImageView proporcional al ancho y limitado en altura. Se añadieron aserciones del margen derecho y reducción en ventanas pequeñas. La animación queda pendiente de fotogramas aprobados; se documentan formato, duración, reposo, alternativa estática y reducción de movimiento.
* **Qué se acepta, modifica o descarta y por qué:** Se conserva exactamente el PNG original y los contratos funcionales. No se deforma el personaje ni se improvisa una animación. No se integra PR #27 ni se modifica App.java o lógica comercial. Se usa un arranque temporal ignorado en target para abrir la aplicación real, autenticando por el formulario existente con cuentas memory y dejando el dashboard maximizado para revisión.
* **Verificación realizada:** Temurin 17.0.21+9, dirigida 18/18; Maven offline clean verify: 841 casos, 781 aprobados, 60 PostgreSQL omitidos, 0 fallos/errores. Escenas reales a 1280×800, 1024×768, 800×600, 700×600 y área de trabajo 1536×816. Se comprobaron visibilidad y maximización nativas de la ventana real. Una captura de escritorio obstruida se descartó; no se presenta como evidencia del banner. Sin SQL ni migraciones. Entradas anteriores intactas.
* **Commit o Pull Request relacionado:** PR #25 Draft; cambios solo locales, sin commit/push, pendientes de aprobación visual del usuario.
