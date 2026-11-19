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
