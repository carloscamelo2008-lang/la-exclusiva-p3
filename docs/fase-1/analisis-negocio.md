# Análisis del negocio: La Exclusiva

**Proyecto:** Sistema de administración para La Exclusiva  
**Asignatura:** Programación de Computadores III  
**Versión:** 0.1  
**Estado:** Borrador para revisión del equipo

## 1. Propósito

Este documento recoge el contexto conocido del negocio familiar La Exclusiva, identifica problemas de sus procesos actuales y plantea las necesidades que deberá atender el sistema. Servirá como base para especificar los requerimientos, diseñar las clases y organizar el trabajo de desarrollo.

## 2. Contexto del negocio

La Exclusiva es un negocio familiar dedicado a la venta de cervezas de diferentes marcas y presentaciones. Sus operaciones incluyen recibir mercancía, mantener existencias, atender pedidos, registrar ventas, cobrar a clientes, realizar abonos, comprar hielo y otros insumos, pagar gastos y controlar distintos movimientos de dinero.

Entre las referencias conocidas se encuentran:

| Referencia | Presentación | Unidades aproximadas por canasta |
|---|---:|---:|
| Costeñita | 175 ml | 38 |
| Costeña Bacana | 330 ml | 30 |
| Poker | 750 ml | 16 |
| Poker | 250 ml | 38 |
| Aguilita | 250 ml | 38 |
| Cola y Pola | Lata de 330 ml | 30 |

La lista puede ampliarse según la demanda. Las cantidades de la tabla corresponden a la información compartida por el negocio y deben validarse antes de convertirse en datos iniciales definitivos.

## 3. Presentaciones y envases

El inventario no puede asumir que todas las canastas contienen la misma cantidad. Las referencias pueden venderse por unidad o por una fracción o canasta completa, por lo que el sistema debe convertir cada formato a una cantidad de producto consistente.

También existen envases retornables. Algunos productos usan tipos de envase diferentes; Poker de 250 ml y Aguilita de 250 ml comparten un tipo, mientras que otras referencias usan envases distintos. Hay envases vacíos que actualmente no se aprovechan mucho. El modelo debe distinguir el inventario del producto vendido y el control de los envases retornables.

Las cavas son recipientes físicos que pueden entregarse temporalmente con pedidos para colocar una canasta de cerveza y hielo. Se necesita poder identificar cada cava, su estado y, cuando esté prestada, la ubicación o persona responsable.

## 4. Situación actual y dificultades

Parte de la información se lleva manualmente. Las deudas de los clientes se anotan en un tablero con el nombre y el valor pendiente. Este método es rápido, pero puede dificultar reconstruir qué productos originaron la deuda, cuándo ocurrió la venta, qué abonos se han realizado y cuál es el saldo correcto.

El dinero se administra de forma manual, sin un registro completo y consistente de todas las entradas, salidas y retiros. Esto limita la posibilidad de conocer el movimiento de caja y analizar los resultados del negocio.

Entre las operaciones que necesitan mejor trazabilidad están:

- Ventas con varias referencias y cantidades.
- Actualización de existencias a partir de las ventas y compras.
- Deudas de clientes, abonos y saldos.
- Compras y pagos a proveedores.
- Gastos como hielo, servicios y pagos a trabajadores.
- Retiros efectuados por los administradores.
- Control de envases retornables y cavas prestadas.

## 5. Justificación

Un sistema centralizado puede reducir la dependencia de registros manuales, conservar el historial de las operaciones y facilitar la consulta de inventario, ventas, deudas y movimientos de dinero.

La solución se desarrollará en Java, inicialmente con interfaz de consola para la primera entrega, persistencia en PostgreSQL y organización por capas. JavaFX queda contemplado para la entrega final. La separación de responsabilidades busca que el sistema sea entendible y permita incorporar funcionalidades sin concentrar toda la lógica en el menú.

## 6. Objetivo general propuesto

Desarrollar un sistema de administración para el negocio familiar La Exclusiva que permita organizar y controlar sus operaciones principales de inventario, ventas, cuentas por cobrar y movimientos de dinero, mediante una aplicación Java con persistencia en PostgreSQL, para mejorar la trazabilidad de la información y apoyar la toma de decisiones.

## 7. Objetivos específicos propuestos

1. Analizar los procesos del negocio y documentar las reglas de operación relevantes.
2. Diseñar una arquitectura por capas y un modelo de clases que distribuya las responsabilidades de forma coherente.
3. Implementar el registro y consulta de productos, presentaciones y existencias.
4. Implementar el registro de ventas y la actualización correspondiente del inventario.
5. Registrar clientes, deudas, abonos y saldos pendientes.
6. Incorporar consultas e informes básicos para facilitar la administración diaria.
7. Aplicar validaciones, manejo de excepciones y buenas prácticas de diseño orientado a objetos.
8. Gestionar el trabajo mediante Git y GitHub, con cambios revisados y trazables.

## 8. Alcance inicial

### Funcionalidades prioritarias para la primera demostración

- Registrar y consultar referencias de productos.
- Registrar y consultar existencias.
- Registrar ventas con varias referencias y cantidades.
- Calcular el total de la venta.
- Actualizar inventario sin permitir existencias negativas.
- Registrar clientes.
- Registrar ventas a crédito, abonos y saldo pendiente, de acuerdo con las reglas confirmadas por el equipo.
- Conservar los datos en PostgreSQL entre ejecuciones.

### Funcionalidades complementarias

Cuando el núcleo esté estable, se priorizarán los movimientos de caja y reportes. Para una ampliación posterior se contemplan compras y créditos con proveedores, control de envases, cavas, pagos a trabajadores y una interfaz JavaFX.

## 9. Reglas de negocio identificadas

1. Cada referencia comercial debe poder identificarse de manera única.
2. La capacidad de una canasta depende de la referencia y no debe asumirse igual para todos los productos.
3. Una venta puede incluir distintas referencias y cantidades.
4. El precio y la cantidad de cada línea deben permitir calcular el total correctamente.
5. Una venta no puede generar inventario negativo.
6. Las deudas deben quedar vinculadas a la venta que las originó.
7. Los abonos deben reducir el saldo pendiente y conservar el historial de pagos.
8. El sistema debe distinguir el valor total vendido del dinero efectivamente recibido.
9. Todo ingreso o egreso registrado debe conservar valor, fecha, categoría y descripción.
10. Los envases retornables deben controlarse según su tipo.
11. Una cava prestada debe poder identificarse y mantener un estado y ubicación actualizados.

## 10. Decisiones por confirmar

Antes de cerrar el diseño, el equipo debe validar:

- Cómo se cuenta el inventario: unidades, canastas o una combinación.
- Los precios y equivalencias para unidad, media canasta y canasta completa.
- Qué tipos de envases se comparten entre referencias y cómo se registran las devoluciones.
- Si las ventas pueden tener pagos parciales y cómo se registra un abono.
- Qué categorías exactas se usarán para ingresos y egresos.
- Cómo se registrarán compras de mercancía y pagos a proveedores.
- Qué datos son obligatorios para clientes y trabajadores.
- Qué operaciones deben estar obligatoriamente listas para la entrega inicial del miércoles.

## 11. Criterio de cierre

El análisis estará listo para usarse como especificación cuando el equipo valide las reglas pendientes que afectan el modelo de datos, las clases y el cálculo de ventas. A partir de ahí se cerrarán los requerimientos, el diseño de clases y los criterios de prueba.
