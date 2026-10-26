# La Exclusiva

Sistema de administración para el negocio familiar La Exclusiva, desarrollado como proyecto de aula de Programación de Computadores III.

## Propósito

Organizar la operación del negocio mediante módulos de productos e inventario, ventas, clientes, pagos parciales y abonos, pedidos de mercancía y movimientos de dinero. El control detallado de envases retornables y cavas físicas se considera una ampliación posterior.

## Estado del proyecto

El repositorio está en la fase inicial de análisis, diseño y preparación del desarrollo.

- **Primer avance:** aplicación de consola en español y funcionalidades prioritarias.
- **Interfaz final:** JavaFX se contempla para la entrega final; los mockups visuales se diseñarán por separado antes de implementar las ventanas.
- **Persistencia del primer avance:** se utilizarán colecciones en memoria detrás de repositorios. Los datos temporales no sobrevivirán al cierre del programa.
- **Base de datos:** PostgreSQL y JDBC quedan para una etapa posterior. La integración no está implementada.
- **Diseño:** análisis, requerimientos, diagramas iniciales y propuesta GRASP/SOLID en revisión.

## Tecnologías

- Java 17.
- Maven.
- Git y GitHub.
- IntelliJ IDEA o NetBeans.
- PostgreSQL con JDBC, previsto para la etapa posterior.

## Ejecutar el proyecto

Se requiere Java 17 y Maven.

Compilar y comprobar:

~~~~bash
mvn clean verify
~~~~

Ejecutar el menú inicial:

~~~~bash
mvn exec:java
~~~~

El menú actual es un esqueleto inicial. Las funcionalidades se implementarán mediante ramas de trabajo y Pull Requests revisados.

## Organización del código

- **model:** conceptos del negocio y reglas propias de cada entidad.
- **persistence:** contratos de repositorio e implementaciones temporales en memoria para el primer avance.
- **service:** casos de uso, validaciones y reglas que coordinan varias entidades.
- **ui:** interacción con el usuario. Para este avance se utilizará consola; JavaFX se abordará en la entrega final.

La interfaz no debe ejecutar SQL ni modificar directamente el inventario. Las operaciones se solicitan a los servicios. Al integrar PostgreSQL, se sustituirá la persistencia temporal detrás de los contratos acordados.

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

- **main:** versión estable.
- **develop:** integración del trabajo revisado.
- Las ramas de trabajo nacen de develop.
- Cada tarea se propone mediante un Pull Request hacia develop.
- El autor no aprueba su propio Pull Request.
- Jesús y Daniel deben revisar y aprobar la configuración inicial antes de que Carlos la integre en develop.

## Verificación automática

GitHub Actions ejecuta una construcción Maven con Java 17 en los cambios configurados. La verificación automática complementa, pero no reemplaza, la revisión de los integrantes.

~~~~bash
mvn --batch-mode --no-transfer-progress clean verify
~~~~

## Documentación del proyecto

- [Documento general del proyecto](docs/fase-1/proyecto-aula.md)
- [Análisis del negocio](docs/fase-1/analisis-negocio.md)
- [Requerimientos](docs/fase-1/requerimientos.md)
- [Tabla de tareas actualizable](docs/fase-1/tabla-tareas.md)
- [Arquitectura inicial por capas](docs/fase-2/arquitectura-inicial.md)
- [Diagrama de paquetes](docs/fase-2/diagrama-paquetes.md)
- [Diagrama de clases inicial](docs/fase-2/diagrama-clases-inicial.md)
- [Estado de los mockups visuales](docs/fase-2/mockups.md)
- [Propuesta GRASP y SOLID](docs/fase-2/grasp-solid.md)
- [Plan de pruebas funcionales](docs/fase-3/pruebas-funcionales.md)
- [Trazabilidad inicial GRASP/SOLID](docs/fase-3/trazabilidad-grasp-solid.md)
- [Lista de control de la fase 4](docs/fase-4/README.md)
- [Bitácoras de uso de IA](docs/ai-usage/README.md)
- [Preparación futura de PostgreSQL](database/README.md)

Los diagramas y las justificaciones de diseño son preliminares. Deben revisarse y actualizarse para reflejar la implementación real.
