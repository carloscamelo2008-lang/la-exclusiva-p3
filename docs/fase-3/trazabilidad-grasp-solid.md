# Trazabilidad inicial de GRASP, SOLID y Código Limpio

**Estado:** matriz inicial de diseño. Actualizarla durante el desarrollo y confirmar cada fila contra código real antes de la entrega final.

| ID | Principio o práctica | Componente previsto | Justificación inicial | Estado |
|---|---|---|---|---|
| TR-01 | Information Expert | BeerProduct | El producto conoce su capacidad por canasta y su cantidad de unidades. | Propuesto |
| TR-02 | Information Expert | Sale | La venta puede calcular su total a partir de las líneas y su saldo a partir de los pagos. | Propuesto |
| TR-03 | Creator | Sale / SaleItem | La venta mantiene sus líneas de detalle. | Propuesto |
| TR-04 | Controller | SaleService | Coordina el caso de uso de registrar venta. | Propuesto |
| TR-05 | Low Coupling | UI / Service / Persistence | La interfaz no accede directamente a la persistencia. | Propuesto |
| TR-06 | High Cohesion | Servicios por módulo | Cada servicio agrupa operaciones relacionadas. | Propuesto |
| TR-07 | Polymorphism | SalePresentation | Cada presentación calcula unidades e importe mediante el mismo contrato. | Propuesto |
| TR-08 | SRP | ConsoleMenu, servicios y repositorios | Separar interacción, reglas de negocio y almacenamiento. | Propuesto |
| TR-09 | OCP | SalePresentation | Añadir presentaciones con nuevas implementaciones del contrato. | Propuesto |
| TR-10 | LSP | Person, Customer y Seller | Los roles conservan el contrato de los datos comunes de persona. | Propuesto |
| TR-11 | ISP | Interfaces de repositorio por módulo | Evitar contratos de persistencia demasiado amplios. | Propuesto |
| TR-12 | DIP | Servicios y abstracciones de repositorio | Separar las reglas de negocio del mecanismo de almacenamiento. | Propuesto |
| TR-13 | Código Limpio: nombres significativos | Clases, métodos y variables | Identificadores técnicos en inglés, descriptivos y consistentes. | Por verificar en el código |
| TR-14 | Código Limpio: métodos pequeños y cohesionados | Servicios y validadores | Cada método debe ejecutar una tarea comprensible y concreta. | Por verificar en el código |
| TR-15 | JavaDoc y comentarios útiles | Clases públicas y reglas no obvias | Documentar en español el propósito y los contratos relevantes sin comentar lo evidente. | Por verificar en el código |
| TR-16 | Manejo de excepciones | Servicios, persistencia y UI | Los errores esperados deben tratarse y mostrarse en español sin exponer trazas técnicas al usuario. | Por implementar |

## Cómo mantener la trazabilidad

- Al terminar una tarea, identificar la clase o método concreto que implementa el principio.
- Cambiar el estado a **Implementado** solo cuando el código exista.
- Añadir una referencia al archivo y método, y una prueba o ejemplo que respalde la afirmación.
- Si una propuesta no encaja con el diseño final, cambiarla o eliminarla en lugar de forzar su aplicación.
- El líder consolida las evidencias de los tres integrantes antes de la documentación final.
