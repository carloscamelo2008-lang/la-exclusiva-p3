# La Exclusiva

Sistema de administración para el negocio familiar La Exclusiva, desarrollado como proyecto de aula de Programación de Computadores III.

## Propósito

Organizar la operación de un negocio familiar de venta de cervezas mediante módulos para productos e inventario, ventas, clientes, cuentas por cobrar, abonos, compras, movimientos de dinero y, en una etapa posterior, control de envases retornables y cavas.

## Estado del proyecto

El repositorio está en la fase inicial de análisis, diseño y preparación del desarrollo.

- **Entrega inicial:** aplicación de consola en español y funcionalidades prioritarias del negocio.
- **Interfaz final:** se contempla JavaFX; la interfaz gráfica no forma parte del primer avance funcional.
- **Base de datos:** PostgreSQL está previsto para una etapa posterior. El esquema y la integración JDBC todavía no están implementados.
- **Diseño:** se preparan desde ahora los diagramas, mockups, responsabilidades de clases y trazabilidad de GRASP/SOLID.

## Tecnologías y herramientas

- Java 17.
- Maven.
- Git y GitHub.
- IntelliJ IDEA o NetBeans.
- PostgreSQL con JDBC, previsto para la etapa de persistencia definitiva.

Maven centraliza la compilación y las dependencias para que los tres integrantes trabajen con el mismo proyecto aunque utilicen diferentes IDE.

## Ejecutar el esqueleto inicial

Se requiere Java 17 y Maven.

Compilar:

~~~~bash
mvn clean compile
~~~~

Ejecutar el menú de consola inicial:

~~~~bash
mvn exec:java
~~~~

El menú actual es una base inicial; sus opciones funcionales se implementarán mediante ramas de trabajo y Pull Requests.

## Organización del código

La arquitectura propuesta separa estas responsabilidades:

- **model:** conceptos y reglas propias de las entidades del negocio.
- **persistence:** acceso a los datos y repositorios.
- **service:** casos de uso, reglas de negocio y coordinación de operaciones.
- **ui:** interacción con el usuario. Para la primera entrega se utilizará consola; JavaFX se abordará en la entrega final.

La interfaz no debe ejecutar SQL ni modificar directamente el inventario. Las operaciones se solicitan a los servicios.

## Convenciones

- Clases, métodos, atributos y variables: inglés.
- Comentarios, JavaDoc, documentación y mensajes visibles al usuario: español.
- Commits y ramas: inglés, descriptivos y consistentes.
- No guardar contraseñas ni datos personales reales en el repositorio.
- No integrar cambios en develop sin la revisión acordada.

## Control de versiones

~~~~text
main
  ↑
develop
  ↑
feature/*  fix/*  refactor/*  docs/*
~~~~

- main: versión estable.
- develop: integración del trabajo revisado.
- Las ramas de trabajo nacen de develop.
- Cada tarea se propone mediante un Pull Request hacia develop.
- El autor no aprueba su propio Pull Request.
- La configuración inicial debe ser revisada por Jesús y Daniel antes de que el líder la integre en develop.

## Documentación del proyecto

- [Documentación general de la fase 1](docs/fase-1/proyecto-aula.md)
- [Análisis del negocio](docs/fase-1/analisis-negocio.md)
- [Requerimientos](docs/fase-1/requerimientos.md)
- [Tabla de tareas actualizable](docs/fase-1/tabla-tareas.md)
- [Arquitectura inicial por capas](docs/fase-2/arquitectura-inicial.md)
- [Diagrama de paquetes](docs/fase-2/diagrama-paquetes.md)
- [Diagrama de clases inicial](docs/fase-2/diagrama-clases-inicial.md)
- [Mockups de la interfaz final](docs/fase-2/mockups.md)
- [Propuesta GRASP y SOLID](docs/fase-2/grasp-solid.md)
- [Trazabilidad inicial GRASP/SOLID](docs/fase-3/trazabilidad-grasp-solid.md)
- [Bitácoras de uso de IA](docs/ai-usage/README.md)
- [Preparación futura de PostgreSQL](database/README.md)

Los diagramas, mockups y trazabilidad se consideran propuestas de diseño hasta que el equipo revise el contenido y se contrasten con la implementación real.
