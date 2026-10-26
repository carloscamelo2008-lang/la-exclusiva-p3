# Plan inmediato de trabajo

**Ventana de planificación:** domingo 25 a miércoles 28 de octubre de 2026.  
**Objetivo:** llegar a la entrega inicial con una base ordenada y un flujo funcional demostrable en consola. Este plan es una meta de trabajo, no una afirmación de avance ya conseguido.

## Domingo 25: base y acuerdo del equipo

- Revisar la configuración inicial y el Pull Request de bootstrap.
- Confirmar las reglas de negocio que afectan inventario, ventas, fiados y abonos.
- Acordar el modelo inicial de datos y las firmas de los servicios que los módulos compartan.
- Crear tareas independientes y ramas después de aprobar la base en develop.

**Resultado esperado:** decisiones básicas acordadas, repositorio común y tareas asignadas.

## Lunes 26: productos e integración básica

- Implementar registro y consulta de productos.
- Definir el esquema inicial de PostgreSQL.
- Implementar conexión y primera persistencia.
- Preparar pruebas básicas de registro y consulta de inventario.

**Resultado esperado:** productos guardados en la base y disponibles después de reiniciar la aplicación.

## Martes 27: ventas, clientes y pagos

- Implementar ventas con varias referencias y cantidades.
- Validar existencias y actualizar inventario.
- Integrar clientes, fiados y abonos según las reglas confirmadas.
- Realizar pruebas de recorrido completo y corregir errores de integración.

**Resultado esperado:** demostración de venta válida y una operación a crédito con abono y saldo consultable, si las dependencias quedan integradas a tiempo.

## Miércoles 28: estabilización y entrega

- Congelar nuevas funcionalidades no esenciales.
- Corregir errores que bloqueen la demostración.
- Verificar compilación, persistencia y casos de negocio críticos.
- Revisar README, documentación y estado de Pull Requests.
- Preparar un guion corto de demostración con datos ficticios.

**Resultado esperado:** versión estable que el equipo pueda ejecutar y explicar.

## Reglas para proteger el plazo

1. No empezar módulos P2 mientras las operaciones P0 no estén integradas.
2. Cada tarea debe tener un responsable, criterio de aceptación y pruebas.
3. Avisar de inmediato si una dependencia compartida bloquea a otro integrante.
4. Integrar con frecuencia en lugar de acumular cambios hasta el último día.
5. El martes se priorizan correcciones y recorridos completos; no se agregan funciones grandes nuevas.
