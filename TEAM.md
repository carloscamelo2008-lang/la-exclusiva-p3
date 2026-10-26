# Organización del equipo

## Información general

- **Proyecto:** La Exclusiva
- **Asignatura:** Programación de Computadores III
- **Repositorio:** https://github.com/carloscamelo2008-lang/la-exclusiva-p3
- **Integrantes:** Carlos Eduardo Camelo Montaño, Jesús Daniel Díaz Álvarez y Daniel Josué Arrieta Fontalvo.

## Roles y responsabilidades iniciales

Esta es la distribución de partida. El equipo debe confirmar los límites entre módulos y las firmas compartidas durante la revisión de la configuración inicial.

### Carlos Eduardo Camelo Montaño — Líder técnico e integración

- Coordinar el análisis, las prioridades y los acuerdos técnicos.
- Mantener el repositorio, la estrategia de ramas y la tabla de tareas.
- Coordinar el menú de consola y el caso de uso de registro de ventas.
- Implementar la integración de ventas con inventario, pagos, abonos y movimientos de caja.
- Coordinar los contratos entre módulos y revisar los Pull Requests.
- Mantener la documentación general y verificar que corresponda con el sistema implementado.
- Preparar la futura integración de PostgreSQL y coordinar la interfaz JavaFX de la entrega final.

### Jesús Daniel Díaz Álvarez — Productos, inventario y pedidos de mercancía

- Definir y documentar las referencias, presentaciones, capacidades de canastas y tipos de envase.
- Implementar el registro y consulta de productos.
- Implementar el control de canastas completas y unidades sueltas.
- Implementar el módulo de pedidos de mercancía y la recepción real de productos, coordinado con la actualización de inventario.
- Preparar pruebas de cantidades, precios por presentación y actualización de existencias.
- Documentar las decisiones técnicas de su módulo.

### Daniel Josué Arrieta Fontalvo — Clientes, fiados y abonos

- Analizar y documentar el registro actual de clientes, deudas y abonos.
- Implementar el registro y consulta de clientes con identificador generado automáticamente.
- Implementar las operaciones para cuentas por cobrar, abonos y saldos pendientes.
- Preparar pruebas para deudas nuevas, pagos parciales, abonos y saldos.
- Coordinar el registro de vendedores, cuyos datos mínimos son nombre, teléfono y dirección.
- Documentar las decisiones técnicas de su módulo.

## Responsabilidades compartidas

Todos los integrantes deberán:

- Revisar el análisis, los requerimientos y los diagramas antes de cerrar el diseño.
- Acordar las clases compartidas, las firmas de métodos y los contratos entre módulos antes de desarrollarlos en paralelo.
- Mantener los cambios limitados a su tarea y coordinar antes de modificar código de otro integrante.
- Compilar y probar su rama antes de solicitar una revisión.
- Revisar el trabajo de otro integrante.
- Registrar el uso de IA en su bitácora personal cuando corresponda.
- Participar en las pruebas integrales, en la actualización de los documentos y en la demostración.

## Flujo Git y autorización de cambios

### Ramas principales

- main: versión estable y lista para una entrega.
- develop: integración del trabajo revisado.
- feature/nombre-funcionalidad: nuevas funcionalidades.
- fix/nombre-defecto: correcciones de errores.
- refactor/nombre-cambio: reorganización interna sin cambiar el comportamiento esperado.
- docs/nombre-documento: cambios de documentación.

Todas las ramas de trabajo se crean desde develop y se proponen mediante Pull Request dirigido a develop.

### Revisión y autorización

1. El autor explica el cambio en el Pull Request e indica cómo lo verificó.
2. Otro integrante revisa el cambio; el autor no puede aprobar su propio Pull Request.
3. Si se solicitan correcciones, el autor las realiza en la misma rama y vuelve a pedir revisión.
4. El líder técnico comprueba que la revisión esté aprobada antes de integrar.
5. No se fusiona un Pull Request mientras tenga observaciones bloqueantes.
6. Para la configuración inicial, Jesús y Daniel revisarán la rama feature/project-setup y deberán aprobarla antes de que Carlos haga el merge a develop.
7. La aprobación del revisor y la verificación automática de Maven son comprobaciones separadas; ambas deben completarse antes de integrar.

## Convenciones

- Identificadores técnicos del código: inglés.
- Comentarios, JavaDoc y documentación: español.
- Textos y mensajes dirigidos al usuario: español.
- Commits: inglés con formato Conventional Commits.
- No subir contraseñas, archivos de entorno reales, compilados ni datos personales reales.

Ejemplos de commits:

- chore: configure Maven project
- docs: document initial business analysis
- feat: add product registration
- fix: validate available stock

## Criterios para cerrar una tarea

Una tarea se considera terminada cuando:

- Cumple su criterio de aceptación.
- Valida entradas y reglas de negocio relevantes.
- Guarda y recupera los datos según el mecanismo aprobado para esa etapa.
- Los mensajes visibles al usuario están en español.
- Compila y tiene pruebas pertinentes.
- Su Pull Request fue revisado, aprobado y fusionado en develop.
- La documentación afectada está actualizada.
