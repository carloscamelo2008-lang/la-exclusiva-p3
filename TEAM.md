# Organización del equipo

## Información general

- **Proyecto:** La Exclusiva
- **Asignatura:** Programación de Computadores III
- **Repositorio:** https://github.com/carloscamelo2008-lang/la-exclusiva-p3
- **Integrantes:** Carlos Eduardo Camelo Montaño, Jesús Daniel Díaz Álvarez y Daniel Josué Arrieta Fontalvo.

## Roles iniciales

### Carlos Eduardo Camelo Montaño — Liderazgo técnico e integración

- Mantener la estrategia de Git, la tabla de tareas y la documentación compartida.
- Coordinar el menú de consola y el caso de uso de registro de ventas.
- Implementar el flujo de ventas y coordinar su integración con inventario, pagos, abonos y caja.
- Acordar contratos entre módulos y revisar los Pull Requests.
- Consolidar pruebas, documentos de diseño y trazabilidad de GRASP/SOLID.
- Coordinar la futura integración de PostgreSQL y la interfaz JavaFX final.

### Jesús Daniel Díaz Álvarez — Productos, inventario y pedidos

- Confirmar y documentar referencias, precios, capacidades de canastas y tipos de envase.
- Implementar catálogo, registro y consulta de productos.
- Implementar inventario combinado de canastas completas y unidades sueltas.
- Implementar precios por unidad, media canasta y canasta completa.
- Desarrollar el módulo separado de pedidos y recepción de mercancía después de estabilizar el núcleo.
- Preparar pruebas de cantidades, presentaciones y existencias.

### Daniel Josué Arrieta Fontalvo — Clientes, vendedores y cuentas por cobrar

- Implementar registro y consulta de clientes con ID generado automáticamente.
- Validar nombre, teléfono y dirección para clientes y vendedores.
- Implementar pagos parciales, abonos, historial y consulta de saldos.
- Preparar pruebas de deudas, abonos parciales y saldos.
- Mantener documentación y pruebas del módulo.

## Responsabilidades compartidas

Todos los integrantes deben revisar reglas de negocio y diagramas, acordar firmas compartidas antes de programar en paralelo, compilar y probar sus ramas, participar en revisiones cruzadas y actualizar su propia bitácora de IA cuando corresponda.

## Flujo Git

- main: versión estable.
- develop: integración de cambios revisados.
- feature/*: nuevas funcionalidades.
- fix/*: correcciones.
- refactor/*: reorganizaciones internas.
- docs/*: documentación.

Las ramas de trabajo se crean desde develop y se integran mediante Pull Requests a develop.

### Autorización de cambios

1. El autor describe el cambio y las pruebas en el Pull Request.
2. El autor no aprueba su propio Pull Request.
3. Los cambios funcionales requieren revisión de otro integrante y visto bueno del líder antes de integrarse.
4. Las decisiones de arquitectura, contratos compartidos o cambios de alcance deben revisarse por los tres integrantes.
5. Para la configuración inicial, Jesús y Daniel revisan y aprueban el PR de feature/project-setup antes de que Carlos lo integre en develop.
6. La compilación automática de Maven y la aprobación del equipo son controles separados; ambos deben ser satisfactorios.

## Convenciones

- Identificadores técnicos del código: inglés.
- Comentarios, JavaDoc, documentación y mensajes de usuario: español.
- Commits y ramas: inglés, descriptivos y consistentes.
- No subir contraseñas, archivos de entorno reales, compilados ni datos personales reales.

Ejemplos de commits:
- chore: configure Maven project
- docs: document initial business analysis
- feat: add product registration
- fix: validate available stock

## Criterio de finalización de una tarea

Una tarea se completa cuando cumple su criterio de aceptación, ha sido probada, compila, tiene documentación actualizada y su Pull Request está aprobado e integrado en develop.
