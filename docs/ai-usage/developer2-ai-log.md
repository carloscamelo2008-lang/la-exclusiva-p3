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
