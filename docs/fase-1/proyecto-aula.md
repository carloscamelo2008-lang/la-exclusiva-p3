# Proyecto de aula: La Exclusiva

## Información general

- **Universidad:** Universidad Popular del Cesar.
- **Programa:** Ingeniería de Sistemas.
- **Asignatura:** Programación de Computadores III (SS462).
- **Docente:** Ing. Esp. Alfredo Bautista.
- **Modalidad:** equipo de tres integrantes.
- **Integrantes:** Carlos Eduardo Camelo Montaño, Jesús Daniel Díaz Álvarez y Daniel Josué Arrieta Fontalvo.
- **Repositorio:** https://github.com/carloscamelo2008-lang/la-exclusiva-p3

## Resumen

La Exclusiva es un proyecto académico orientado al desarrollo de un sistema de administración para un negocio familiar dedicado a la venta de cervezas. La propuesta surge de la necesidad de organizar el catálogo, el inventario por canastas y unidades sueltas, las ventas, los pagos parciales, las deudas de clientes y los movimientos de dinero. La solución se diseñará con programación orientada a objetos y arquitectura por capas. Para el avance inmediato se trabajará con una interfaz de consola; la interfaz JavaFX y la integración definitiva con PostgreSQL se abordarán en una etapa posterior. Se aplicarán principios de diseño, validaciones, manejo de excepciones y control de versiones mediante Git y GitHub.

## 1. Introducción

Los pequeños negocios familiares suelen administrar su operación con herramientas simples que resultan prácticas al inicio, pero que no siempre permiten reconstruir con facilidad el historial de una venta, un abono o una modificación del inventario. La Exclusiva presenta procesos que necesitan un registro más organizado: venta de distintas referencias y presentaciones, canastas con capacidades diferentes, envases retornables, ventas a crédito y pagos parciales.

El proyecto pretende resolver esas necesidades de manera gradual. Se comenzará por el análisis del negocio, la definición de requerimientos y el diseño orientado a objetos. Después se implementarán los casos de uso prioritarios, se probará su integración y se completará la documentación con la evidencia del código real.

## 2. Planteamiento del problema

### 2.1 Identificación del problema

Parte de la información relacionada con deudas, abonos, inventario y movimientos del dinero se registra manualmente. Este método puede producir diferencias entre el inventario físico y el anotado, dificultar el seguimiento de una cuenta por cobrar y no distinguir claramente entre el valor vendido y el dinero que efectivamente ingresó a caja.

La variedad de presentaciones también complica el conteo. Algunas canastas contienen 38 unidades, otras 30 y la referencia de 750 ml contiene 16. Adicionalmente, algunas referencias comparten envases retornables mientras que otras usan envases exclusivos o latas no retornables.

### 2.2 Justificación

Un sistema de administración puede centralizar la información de las operaciones, conservar los datos de las ventas y abonos, calcular los saldos pendientes y mantener actualizadas las existencias. También puede diferenciar los movimientos de dinero según su motivo, lo cual facilita analizar los gastos y las entradas reales.

Desde el punto de vista académico, el proyecto permite aplicar clases, herencia, encapsulamiento, polimorfismo, colecciones, arquitectura por capas, validaciones, manejo de excepciones, pruebas y principios de diseño. La estrategia de Git ayuda a separar el trabajo de cada integrante y permite revisar los cambios antes de integrarlos.

### 2.3 Estado del arte y vigilancia tecnológica

Los sistemas de punto de venta existentes ofrecen funcionalidades que sirven como referencia para el alcance del proyecto.

- **Loyverse POS** documenta funciones para registrar ventas y distintos medios de pago, administrar inventario, manejar compras y proveedores, registrar movimientos de caja y consultar informes de ventas. Su apartado de inventario avanzado incluye pedidos a proveedores y recepción de mercancía. Fuentes: [funcionalidades de Loyverse](https://loyverse.com/features) y [gestión de inventario](https://loyverse.com/en-us/advanced-inventory).
- **Alegra POS Colombia** describe un punto de venta con registro rápido de ventas, inventario actualizado con movimientos, control de caja y reportes de productos. También presenta opciones de administración de clientes, productos y operaciones de compra. Fuente: [Alegra Punto de Venta Colombia](https://www.alegra.com/colombia/punto-de-venta/).

Estas soluciones muestran que el registro de ventas, los movimientos de inventario, los pedidos de mercancía y el control de caja son funciones habituales en sistemas de administración comercial. La propuesta de La Exclusiva no busca competir con plataformas completas; su valor académico consiste en adaptar el diseño a las reglas particulares del negocio conocido por el equipo: inventario por canastas y unidades sueltas, diferentes capacidades de canasta, tipos de envases retornables, venta por unidad o por canasta y seguimiento simple de fiados y abonos.

**Fecha de consulta de las fuentes:** 25 de octubre de 2026.

## 3. Objetivos

### 3.1 Objetivo general

Desarrollar un sistema de administración para el negocio familiar La Exclusiva que permita organizar y controlar sus operaciones principales de inventario, ventas, cuentas por cobrar y movimientos de dinero, mediante una aplicación Java con arquitectura por capas y una estrategia de persistencia definida por etapas, para mejorar la trazabilidad de la información y apoyar la toma de decisiones.

### 3.2 Objetivos específicos

1. Analizar los procesos del negocio y establecer sus reglas de operación.
2. Definir los requerimientos funcionales y no funcionales con criterios de aceptación.
3. Diseñar la arquitectura por capas, los diagramas de paquetes y clases y las responsabilidades de los componentes.
4. Aplicar y justificar patrones GRASP y principios SOLID a partir de clases y métodos concretos.
5. Implementar el registro de productos, inventario y ventas con validaciones.
6. Registrar clientes, pagos parciales, abonos y saldos pendientes.
7. Incorporar un módulo separado para preparar pedidos de mercancía y registrar su recepción.
8. Registrar los movimientos de dinero con categorías que permitan distinguir entradas y salidas reales.
9. Gestionar los cambios mediante ramas, commits y Pull Requests revisados.
10. Preparar una interfaz gráfica JavaFX para la entrega final y completar la persistencia definitiva con PostgreSQL en la etapa correspondiente.

## 4. Descripción de la solución

La aplicación se desarrollará en Java 17 y Maven. Durante el avance inmediato, la interacción será por consola y todos los mensajes visibles al usuario estarán en español. La arquitectura separará las entidades del negocio, los servicios que implementan los casos de uso, la persistencia y la interfaz.

### 4.1 Módulos funcionales

1. Productos y presentaciones.
2. Inventario de canastas completas y unidades sueltas.
3. Ventas con varias referencias y presentaciones.
4. Clientes, ventas a crédito, pagos parciales y abonos.
5. Pedidos de mercancía y recepción.
6. Movimientos de ingresos y egresos.
7. Consultas y reportes.
8. Control de envases y cavas físicas, como ampliación posterior.

### 4.2 Requerimientos

Los requerimientos con identificadores y criterios de aceptación se mantienen en [requerimientos.md](requerimientos.md). La tabla de actividades y sus estados se actualiza en [tabla-tareas.md](tabla-tareas.md).

### 4.3 Arquitectura y diseño

La arquitectura inicial y los diagramas están en la carpeta docs/fase-2/. Los mockups documentan la interfaz JavaFX futura; no significan que esa interfaz esté implementada en el avance actual.

## 5. Gestión del proyecto con Git

- **Repositorio:** la-exclusiva-p3.
- **URL:** https://github.com/carloscamelo2008-lang/la-exclusiva-p3.
- **Ramas:** main para versiones estables, develop para integración y ramas feature/*, fix/*, refactor/* y docs/* para tareas específicas.
- **Pull Requests:** todos los cambios funcionales se proponen hacia develop; el autor no aprueba su propio cambio. Para la configuración inicial, los dos desarrolladores revisarán y aprobarán el PR del líder antes de integrarlo.
- **Roles:** Carlos coordina la integración y el flujo de ventas; Jesús se enfoca inicialmente en productos, inventario y pedidos; Daniel se enfoca inicialmente en clientes, fiados y abonos. Los contratos compartidos deben acordarse antes del desarrollo paralelo.
- **Seguimiento:** la tabla de tareas se mantiene en el repositorio y debe reflejar el estado real de cada actividad.
- **Bitácoras de IA:** cada integrante conservará un registro de su uso real de herramientas de inteligencia artificial en docs/ai-usage/.

## 6. Conclusiones preliminares

El análisis inicial permite identificar que las prioridades del sistema son mantener un inventario consistente, registrar ventas con precios por presentación y distinguir el dinero recibido de las deudas pendientes. Los pedidos de mercancía y la recepción deben registrarse separadamente, ya que un pedido no implica que la mercancía haya llegado ni que se haya pagado.

La solución se desarrollará por etapas: primero se cerrarán las reglas de negocio y el diseño, luego se integrarán las funcionalidades de consola y posteriormente se trabajará la interfaz JavaFX y la persistencia definitiva con PostgreSQL. Las conclusiones finales se actualizarán una vez que la aplicación se encuentre implementada y probada.

## 7. Bibliografía y fuentes

- Universidad Popular del Cesar. *Proyectos de Aula: Programación de Computadores III (SS462)*. Material suministrado por el docente.
- Loyverse. *Point of Sale Features*. https://loyverse.com/features
- Loyverse. *Inventory Management*. https://loyverse.com/en-us/advanced-inventory
- Alegra Colombia. *Software punto de venta para negocio en Colombia*. https://www.alegra.com/colombia/punto-de-venta/
- GameZone-Entropy. Repositorio de referencia utilizado por el equipo para estudiar la organización del código, la arquitectura por capas y el flujo de trabajo con Git. https://github.com/carloscamelo2008-lang/GameZone-Entropy
