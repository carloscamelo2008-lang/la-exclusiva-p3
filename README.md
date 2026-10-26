# La Exclusiva

Sistema de administración para el negocio familiar La Exclusiva, desarrollado como proyecto de aula de Programación de Computadores III.

## Propósito

Organizar las operaciones de venta de cervezas y el control de información del negocio: productos, presentaciones, inventario, clientes, ventas, cuentas por cobrar y movimientos de dinero. El alcance contempla, además, compras, proveedores, envases retornables, cavas y pagos a trabajadores, según la prioridad acordada por el equipo.

## Estado actual

El repositorio se encuentra en la fase de configuración inicial y análisis. La primera entrega se trabajará con una interfaz de consola; JavaFX queda previsto para la entrega final.

## Tecnologías

- Java 17.
- Maven.
- PostgreSQL mediante JDBC.
- Git y GitHub.
- IntelliJ IDEA y NetBeans como entornos de desarrollo compatibles.

El proyecto se configura mediante Maven para que los integrantes puedan clonarlo y abrirlo desde distintos IDE sin mantener configuraciones de compilación diferentes.

## Ejecutar el proyecto

Se requiere tener instalado Java 17 y Maven.

Compilar:

```bash
mvn clean compile
```

Ejecutar el menú de consola:

```bash
mvn exec:java
```

La estructura inicial del menú es una base de trabajo. Las opciones funcionales se incorporarán gradualmente mediante ramas de funcionalidad y Pull Requests.

## Organización del código

El proyecto seguirá una arquitectura por capas inspirada en el trabajo realizado en GameZone:

- **model:** entidades del negocio y su comportamiento.
- **persistence:** acceso y persistencia en PostgreSQL, mediante clases de acceso a datos.
- **service:** reglas de negocio, validaciones y coordinación de operaciones.
- **ui:** interacción con el usuario mediante consola durante la primera entrega.

La interfaz no accederá directamente a la base de datos. Las operaciones se solicitarán a la capa de servicios, que coordinará el trabajo con las demás capas.

## Convenciones del equipo

- Los nombres de clases, métodos, atributos y variables se escribirán en inglés.
- Los comentarios, JavaDoc y documentos del proyecto se redactarán en español.
- Los menús, mensajes de validación y demás textos visibles al usuario estarán en español.
- Los commits y nombres de ramas utilizarán inglés y nombres descriptivos.
- Las credenciales de conexión no se guardarán en GitHub.
- No se fusionarán cambios a `develop` sin una revisión y aprobación de otro integrante.

## Control de versiones

Se utilizará el siguiente flujo:

```text
main
  ↑
develop
  ↑
feature/*  fix/*  refactor/*  docs/*
```

- `main`: versión estable.
- `develop`: integración del trabajo aprobado.
- Las ramas de trabajo se crean desde `develop`.
- Cada tarea se propone mediante un Pull Request hacia `develop`.
- El autor no aprueba su propio Pull Request.
- La configuración inicial se enviará a revisión de Jesús y Daniel antes de integrarla en `develop`.

## Documentación

- `TEAM.md`: roles, responsabilidades y reglas de trabajo.
- `docs/fase-1/analisis-negocio.md`: contexto del negocio y problemas identificados.
- `docs/fase-1/requerimientos.md`: funcionalidades, prioridades y criterios de aceptación.
- `docs/fase-1/plan-entrega.md`: plan inmediato para la entrega.
- `docs/fase-1/tabla-tareas.md`: responsables y estado de las actividades.
- `docs/fase-2/arquitectura-inicial.md`: capas y dependencias iniciales.
- `database/README.md`: acuerdos iniciales para scripts y conexión a PostgreSQL.

Los documentos iniciales son borradores de trabajo que el equipo debe revisar antes de tomarlos como especificación definitiva.
