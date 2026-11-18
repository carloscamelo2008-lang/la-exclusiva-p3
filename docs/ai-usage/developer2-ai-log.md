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
* **Herramienta:** Codex, con Git, Maven, JUnit y control visual de la aplicación Windows.
* **Fase, tarea y rama:** Fase 3 – Mejoras integrales de navegación, sesión y experiencia visual JavaFX (`feature/javafx-navigation-ux`).
* **Objetivo de la consulta:** Solicité apoyo de la IA para completar una actividad que reuniera el cierre de sesión, el cambio de usuario, la navegación activa, la legibilidad del menú y la aclaración de Cartera como consulta.
* **Resumen de la respuesta recibida:** Codex comprobó el árbol limpio, actualizó las referencias de Git y verificó que el PR #13 estaba integrado. Creó la rama desde `origin/develop` en `a2d83b9616c8d6e429a27c1ff9d7e8bdaec3d151`. Implementó la invalidación de la sesión y su comprobación en autorización, el regreso al Login con campos vacíos y la reutilización de los mismos servicios al cambiar de usuario. Deshabilitó Gestión de Usuarios conservando su permiso y añadió la indicación de pendiente. Agregó resaltado de la sección seleccionada, conservado ante errores o acceso denegado, un menú más legible, mayor contraste del rol y el texto de Cartera como consulta.
* **Qué se acepta, modifica o descarta y por qué:** Autoricé reunir estas mejoras en una actividad completa y una sola entrada de bitácora. Se mantuvo la autenticación existente y se amplió la sesión para poder invalidarla sin desactivar la cuenta. Se reutilizaron los servicios de clientes y vendedores, sin cambiar sus contratos ni la lógica de saldos. Dejé fuera la gestión de usuarios completa, los abonos y la integración JDBC.
* **Verificación realizada:** Codex ejecutó `mvn -Dtest=AuthenticationServiceTest test`: 29 pruebas aprobadas, sin fallos, errores ni omisiones. Ejecutó `mvn clean verify`: 188 pruebas contabilizadas, 186 aprobadas, sin fallos ni errores y 2 omitidas por falta de configuración PostgreSQL. Usó JDK 25.0.2, compilación para Java 17 y JavaFX 17.0.2. Las pruebas cubren login inválido, logout, cambio de administrador a trabajador, nueva sesión tras invalidación, bloqueo del controlador anterior, permisos, selección activa, conservación del panel ante errores y regresiones de Clientes, Vendedores y Cartera. También realizó una revisión visual con cuentas demo y contraseñas temporales de proceso, sin datos reales: selección de las cuatro secciones, botón de Usuarios deshabilitado, Cartera de consulta, maximización y cambio de usuario dentro de la misma aplicación.
* **Errores y soluciones:** Durante la revisión visual, Codex observó que cambiar de escena después de maximizar y restaurar podía dejar el contenido descentrado. Ajustó el tamaño de la ventana al cambiar de escena y restauró el Login a tamaño normal al cerrar sesión. Añadió ese recorrido a la prueba de cambio de usuario y repitió las pruebas dirigidas y completas, con los resultados anteriores. Los logs de consultas y de logout fallidos corresponden a escenarios simulados de las pruebas. Maven y JavaFX mantuvieron advertencias de configuración preexistentes.
* **Limitaciones y trazabilidad:** La revisión previa de pantallas y esta implementación se conservaron como parte de la Actividad 5. Yo autoricé el alcance y Codex realizó los cambios y las verificaciones descritas. Queda pendiente ejecutar con JDK 17 y revisar otros escalados de Windows y tablas con datos largos. Las comprobaciones visuales usaron listados vacíos; los datos preparados y los fallos de consulta se verificaron mediante pruebas automatizadas. Las dos integraciones PostgreSQL omitidas no se ejecutaron ni se abrió conexión a una base. La rama anterior se conservó en `3568abdf15f347ba0b0e56b4715d721b7b6a7f02`.
* **Commit o Pull Request relacionado:** Commit de desarrollo `d1872c3251e044f58bd72a9597a9b46774d357a1`. Pull Request pendiente de creación y de mi revisión. El revisor obligatorio de un futuro PR será `carloscamelo2008-lang`.
