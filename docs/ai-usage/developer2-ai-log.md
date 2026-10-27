\# Bitácora de uso de IA – Desarrollador 2



\*\*Integrante:\*\* Daniel Josué Arrieta Fontalvo  

\*\*Rol inicial:\*\* clientes, fiados y abonos  

\*\*Proyecto:\*\* La Exclusiva  



\## Registro de interacciones



\### Interacción 1

\* \*\*Fecha:\*\* 26/10/2026

\* \*\*Herramienta:\*\* Gemini

\* \*\*Fase, tarea y rama:\*\* Fase 3 - Setup inicial y estructuras de dominio (`feature/clients-module`)

\* \*\*Objetivo de la consulta:\*\* Utilizar la IA como apoyo técnico para revisar y validar la estructura de las clases de dominio `Customer`, `Seller` y la clase de validación `PersonValidator`, asegurando el cumplimiento de las convenciones del equipo (identificadores en inglés y JavaDoc en español).

\* \*\*Resumen de la respuesta recibida:\*\* La herramienta sirvió de soporte para sugerir la implementación de las clases `Customer` (con control de deudas), `Seller` y `PersonValidator` para la comprobación de campos obligatorios.

\* \*\*Qué se aceptó, modificó o descartó y por qué:\*\* Manteniendo la iniciativa del diseño, tomé las sugerencias técnicas para integrarlas en el proyecto, verificando que la lógica de validación respetara los principios SOLID/GRASP y el desacoplamiento de responsabilidades.

\* \*\*Verificación realizada:\*\* Compilación y verificación personal en NetBeans mediante `Clean and Build` con resultado exitoso.

\* \*\*Commit o Pull Request relacionado:\*\* Commit local inicial en `feature/clients-module`.



\---



\### Interacción 2

\* \*\*Fecha:\*\* 26/10/2026

\* \*\*Herramienta:\*\* Gemini

\* \*\*Fase, tarea y rama:\*\* Fase 3 - Repositorios en memoria y servicios (`feature/clients-module`)

\* \*\*Objetivo de la consulta:\*\* Consultar alternativas de apoyo para la capa de persistencia en memoria y la lógica de negocio asociada a clientes, vendedores, abonos y fiados.

\* \*\*Resumen de la respuesta recibida:\*\* La IA funcionó como guía de apoyo proporcionando ejemplos de la estructura para `CustomerRepository`, `SellerRepository` (usando `Map`), `CustomerService` y `SellerService`.

\* \*\*Qué se aceptó, modificó o descartó y por qué:\*\* Por iniciativa propia, lideré la integración del flujo de negocio ajustando las reglas de validación para garantizar que los abonos no excedan la deuda actual y que los fiados solo registren montos válidos.

\* \*\*Verificación realizada:\*\* Ejecución y pruebas de compilación en NetBeans (`BUILD SUCCESS`).

\* \*\*Commit o Pull Request relacionado:\*\* Commit de entrega del módulo en `feature/clients-module`.

