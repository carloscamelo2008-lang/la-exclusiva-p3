# Organización del equipo

## Información general

- **Proyecto:** La Exclusiva
- **Asignatura:** Programación de Computadores III
- **Repositorio:** https://github.com/carloscamelo2008-lang/la-exclusiva-p3
- **Integrantes:** Carlos Eduardo Camelo Montaño, Jesús Daniel Díaz Álvarez y Daniel Josué Arrieta Fontalvo.

## Roles y responsabilidades iniciales

Esta distribución es la propuesta de partida para la primera entrega. El equipo debe confirmar los límites entre módulos durante la revisión de la configuración inicial.

### Carlos Eduardo Camelo Montaño — Líder técnico e integración

- Coordinar el análisis, las prioridades y los acuerdos técnicos.
- Mantener el repositorio, la estrategia de ramas y la tabla de tareas.
- Preparar el punto de entrada de la aplicación y coordinar el menú de consola.
- Implementar el flujo de registro de ventas y coordinar su integración con inventario, clientes y pagos.
- Coordinar la configuración de conexión a PostgreSQL sin publicar credenciales.
- Revisar Pull Requests y coordinar las pruebas integrales.
- Mantener la documentación general y comprobar que coincida con la implementación.

### Jesús Daniel Díaz Álvarez — Productos e inventario

- Definir y documentar las referencias, presentaciones y reglas de inventario.
- Implementar el registro y consulta de productos.
- Implementar las operaciones necesarias para consultar y actualizar existencias.
- Preparar las pruebas de cantidades, equivalencias y actualización de inventario.
- Documentar las decisiones técnicas de su módulo.

### Daniel Josué Arrieta Fontalvo — Clientes, fiados y abonos

- Analizar y documentar el registro actual de clientes, deudas y abonos.
- Implementar el registro y consulta de clientes.
- Implementar las operaciones para cuentas por cobrar, abonos y saldos pendientes.
- Preparar pruebas para deudas nuevas, abonos parciales y saldos calculados.
- Documentar las decisiones técnicas de su módulo.

## Responsabilidades compartidas

Todos los integrantes deberán:

- Revisar el análisis y los requerimientos antes de cerrar el diseño.
- Acordar las clases compartidas, las firmas de métodos y las tablas necesarias antes de implementarlas en paralelo.
- Mantener los cambios limitados a su tarea y evitar editar código de otro integrante sin coordinarlo.
- Compilar y probar su rama antes de solicitar una revisión.
- Revisar el trabajo de otro integrante.
- Mantener sincronizada su rama con `develop` antes de iniciar una tarea nueva.
- Participar en pruebas integrales, revisión de documentación y demostración.

## Flujo Git y autorización de cambios

### Ramas principales

- `main`: versión estable y lista para una entrega.
- `develop`: integración de funcionalidades revisadas.
- `feature/nombre-funcionalidad`: nuevas funcionalidades.
- `fix/nombre-defecto`: correcciones de errores.
- `refactor/nombre-cambio`: reorganización interna sin cambiar el comportamiento esperado.
- `docs/nombre-documento`: cambios de documentación.

Todas las ramas de trabajo se crean desde `develop) y se proponen mediante Pull Request dirigido a `develop).

### Revisión y autorización

1. El autor explica el cambio en el Pull Request y aporta evidencia de compilación o pruebas.
2. Otro integrante revisa el cambio. El autor no puede aprobar su propio Pull Request.
3. Si solicita correcciones, el autor las realiza en la misma rama y vuelve a solicitar revisión.
4. El líder técnico comprueba que la revisión está aprobada antes de integrar.
5. No se fusiona un Pull Request mientras existan observaciones bloqueantes.

**Pull Request de configuración inicial:** Carlos lo abrirá desde `feature/project-setup` hacia `develop`. Jesús y Daniel deben revisar y aprobar la base inicial antes de que Carlos realice el merge. Hasta entonces, no se comenzará el trabajo funcional sobre esa base integrada.

## Convenciones

- Identificadores técnicos del código: inglés.
- Comentarios, JavaDoc y documentación: español.
- Textos y mensajes dirigidos al usuario: español.
- Commits: inglés con formato Conventional Commits.
- No subir contraseñas, archivos de entorno reales, compilados ni datos personales reales.

Ejemplos de mensajes de commit:

- `chore: configure Maven project`
- `docs: document initial business analysis`
- `feat: add product registration`
- `fix: validate available stock`

## Criterios para cerrar una tarea

Una tarea se considera terminada cuando:

- Cumple el criterio de aceptación acordado.
- Valida entradas y reglas de negocio relevantes.
- Guarda y recupera correctamente los datos cuando aplica.
- Los mensajes visibles al usuario están en español.
- Compila y tiene pruebas manuales o automatizadas pertinentes.
- Su Pull Request fue aprobado y fusionado en `develop`.
- La documentación afectada está actualizada.
