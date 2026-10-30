# La Exclusiva

Sistema de administración para el negocio familiar La Exclusiva, desarrollado como proyecto de aula de Programación de Computadores III.

## Propósito

Organizar la operación del negocio mediante módulos de productos e inventario, ventas, clientes, pagos parciales y abonos, pedidos de mercancía y movimientos de dinero. El control detallado de envases retornables y cavas físicas se considera una ampliación posterior.

## Estado del proyecto

La aplicación cuenta con una interfaz de consola en español para productos e inventario, ventas, clientes, vendedores, pagos parciales, abonos y consulta de cartera. La autenticación y los permisos del menú se aplican con cuentas de administrador y trabajador.

- **Primer avance:** aplicación de consola en español y funcionalidades prioritarias.
- **Interfaz final:** JavaFX se contempla para la entrega final; los mockups visuales se diseñarán por separado antes de implementar las ventanas.
- **Persistencia actual:** los repositorios guardan los datos en memoria. Las cuentas, el inventario, clientes, vendedores y ventas no sobreviven al cierre del programa.
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

Ejecutar la aplicación:

~~~~bash
mvn exec:java
~~~~

En el primer inicio normal, la aplicación solicita un nombre de usuario y una contraseña para crear la cuenta inicial de administrador. Después solicita esas credenciales para iniciar sesión. Como las cuentas se guardan en memoria, este aprovisionamiento vuelve a solicitarse en cada ejecución normal.

### Modo demo

El modo demo carga existencias, un cliente ficticio, dos vendedores y una venta parcialmente pagada. También crea las cuentas `admin.demo` y `trabajador.demo`; sus contraseñas se leen de variables de entorno y se usan para iniciar sesión:

~~~~bash
export LAEXCLUSIVA_DEMO_ADMIN_PASSWORD='contraseña-admin'
export LAEXCLUSIVA_DEMO_WORKER_PASSWORD='contraseña-trabajador'
mvn exec:java -Dexec.args="--demo"
~~~~

En PowerShell, configura las mismas variables con `$env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD` y `$env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD` antes de ejecutar `mvn exec:java "-Dexec.args=--demo"`. Si alguna falta o está vacía, la aplicación informa que ambas son obligatorias y termina. El modo demo y sus cuentas también son temporales y se vuelven a crear al iniciar una nueva ejecución.

El administrador tiene todos los permisos definidos. El trabajador puede consultar catálogo e inventario, registrar ventas y clientes, consultar clientes, ventas y cartera, y registrar abonos; no puede ingresar existencias ni consultar o registrar vendedores. En las ventas del trabajador, el vendedor se toma de la cuenta autenticada.

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
