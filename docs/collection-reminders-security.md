# Asistente de cartera, estados de cuenta y recordatorios

## Alcance y arquitectura

`CollectionAssistantService` ofrece recomendaciones explicables por reglas registradas. No usa un modelo de aprendizaje automático, no predice incumplimiento y no califica moralmente al cliente. La composición se obtiene desde `ApplicationServices.collectionAssistant()`; sus adaptadores en memoria y JDBC usan los puertos `CustomerCollectionRepository` y `AdministrativeAuditRepository`. En PostgreSQL, el seguimiento comparte el `JdbcConnectionProvider` y la frontera transaccional de la aplicación.

La información del perfil se almacena aparte de `Customer`, para no reinterpretar ni reescribir sus campos históricos. V107 crea perfiles de contacto, eventos de seguimiento y avisos; V108 crea eventos administrativos. Las migraciones son manuales y no se ejecutan al iniciar la aplicación. Revisar [database/README.md](../database/README.md) antes de preparar cualquier entorno.

## Recomendaciones y antigüedad

El tablero usa saldos vigentes, compromisos y movimientos:

1. Compromiso vencido: prioridad alta.
2. Deuda con antigüedad verificable de 30 días o más: prioridad media.
3. Compromiso dentro de los próximos tres días: próximo seguimiento.
4. Otros saldos positivos: prioridad general.

La prioridad de una fila es una categoría única; los contadores del tablero indican por separado compromisos vencidos, cuentas con deuda antigua, compromisos próximos y avisos pendientes de revisión. Las cuentas saldadas se excluyen. Los pagos posteriores a la observación previa del saldo se señalan cuando la fuente histórica permite verificar el cambio.

La antigüedad de una deuda de venta proviene de la fecha de creación de la venta. Para la deuda general se contrasta el saldo con el libro de movimientos. Como el esquema no asigna abonos parciales a cargos individuales ni establece FIFO, el servicio solo informa una fecha cuando el libro permite demostrar que parte del cargo más antiguo sigue pendiente. En casos ambiguos muestra **«Antigüedad no determinada»**; no estima fechas desde el saldo.

El saldo general se mantiene separado de la deuda pendiente de ventas. Una inconsistencia en la que la deuda de ventas exceda el saldo agregado bloquea recomendaciones, documento o aviso para evitar presentar un total financiero incoherente.

## Seguimiento, consentimiento y datos de contacto

Un administrador puede registrar compromiso, nota breve, correo opcional, canal autorizado, consentimiento y si los recordatorios están habilitados. Cada cambio genera un evento de seguimiento con actor, fecha y saldo observado. Las notas son para gestión breve; no deben incluir contraseñas, datos bancarios, información sensible innecesaria ni detalles de autenticación.

Al revocar consentimiento, el servicio fuerza canal `NONE`, desactiva recordatorios, limpia el correo del perfil y deja un evento de revocación. El teléfono existente del cliente no se convierte automáticamente en autorización para contactar. El canal `PHONE` solo genera una tarea de gestión manual: este sistema no hace llamadas ni puede registrar contactos externos que el equipo realice fuera de la aplicación.

La interfaz de administración de contacto requiere `MANAGE_COLLECTION`. El resumen de alertas y la generación del PDF requieren `VIEW_PORTFOLIO`. El servicio y el controlador revalidan permisos; ocultar campos no es el único control. Los detalles de contacto no se entregan en el resumen destinado a trabajadores.

## Estado de cuenta PDF

`statementPdf(UserSession, customerId)` genera bytes bajo una lectura consistente. Incluye cliente, fecha local de generación, ventas pendientes con líneas/precios históricos y pagos registrados, deuda general por separado, saldo agregado y fecha de compromiso si existe. El documento es una comunicación comercial informativa, **no es factura electrónica DIAN ni comprobante fiscal**. El envío no confirma un pago; todo pago debe registrarse y validarse mediante el flujo de caja/abonos existente.

La llave de pago se lee de `LAEXCLUSIVA_PAYMENT_KEY`, nunca de una constante. Solo se incorpora al documento cuando también están en `true` `LAEXCLUSIVA_PAYMENT_KEY_OWNER_CONFIRMED` y `LAEXCLUSIVA_PAYMENT_KEY_VERIFIED_BY_ADMIN`. Las confirmaciones y el valor son responsabilidad de administración; la aplicación no verifica titularidad en el banco. No almacenes la llave en Git, bitácoras o pruebas.

## Avisos semanales y correo

Un administrador prepara el lote manualmente desde «Actualizar recordatorios». No existe un proceso de fondo: la aplicación de escritorio no envía nada con el equipo apagado. Se prepara como máximo un registro por cliente y semana ISO; la clave única de V107 evita duplicados concurrentes. Solo se incluyen saldos positivos, consentimiento vigente, recordatorios activados y un canal seleccionado. La regla semanal limita el lote de la aplicación a una comunicación preparada por cliente, no permite contabilizar contactos por fuera del sistema.

Cada correo se revisa y confirma de manera individual. La vista previa muestra destinatario, texto y PDF. El modo predeterminado es simulado: no abre una conexión SMTP y el aviso queda pendiente. El envío real requiere `LAEXCLUSIVA_SMTP_ENABLED=true`, servidor TLS correctamente configurado, consentimiento/canal email vigentes, saldo aún idéntico al revisado, instrucciones de pago confirmadas y ventana de contacto habilitada.

Variables SMTP leídas del entorno del proceso:

| Variable | Descripción |
|---|---|
| `LAEXCLUSIVA_SMTP_ENABLED` | Habilita explícitamente entrega real; desactivada por defecto |
| `LAEXCLUSIVA_SMTP_HOST` | Host SMTP |
| `LAEXCLUSIVA_SMTP_PORT` | Puerto TLS configurado |
| `LAEXCLUSIVA_SMTP_USERNAME` | Usuario de correo |
| `LAEXCLUSIVA_SMTP_PASSWORD` | Secreto del proveedor; nunca se registra |
| `LAEXCLUSIVA_SMTP_FROM` | Remitente autorizado |

El proveedor usa TLS, valida certificados/identidad del servidor, fija tiempos de espera y no activa trazas SMTP. Las pruebas usan proveedores dobles o simulados y nunca envían mensajes reales. Una entrega externa y una transacción de base de datos no pueden confirmarse atómicamente: el registro pasa a `SENDING` antes de llamar al proveedor. Si se pierde la respuesta o falla la persistencia posterior, no se reintenta automáticamente; se debe reconciliar el estado con el proveedor antes de cualquier intervención manual.

Los horarios son hora `America/Bogota`: lunes a viernes 07:00–19:00, sábado 08:00–15:00, sin domingos ni festivos configurados. Para habilitar un envío real se debe suministrar el calendario completo del año actual:

| Variable | Descripción |
|---|---|
| `LAEXCLUSIVA_COLOMBIA_HOLIDAYS_YEAR` | Año para el calendario configurado; debe coincidir con el año actual |
| `LAEXCLUSIVA_COLOMBIA_HOLIDAYS` | Fechas festivas del año, separadas por coma (`AAAA-MM-DD`) |

Si falta el calendario, está vencido o contiene valores inválidos, el servicio bloquea el envío. La lista completa y correcta de festivos es una responsabilidad de configuración y debe revisarse cada año. Los controles de la aplicación son una política conservadora, no una certificación legal ni sustituyen la revisión jurídica y operativa.

Referencias normativas primarias: [Ley 2300 de 2023, Función Pública](https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=213990) y [Ley 1581 de 2012, texto oficial](https://www.suin-juriscol.gov.co/archivo/listado/habeasdataSUIN.pdf). La organización debe obtener y conservar evidencia válida del consentimiento y atender revocaciones o solicitudes recibidas por canales externos.

## Auditoría y permisos

`AdministrativeAuditService.list(session, desde, hasta, límite)` exige `VIEW_ADMIN_AUDIT`, y la autoridad de sesión valida cuenta activa, rol y sesión vigente. Se registran usuario creado, cambios de rol, activación/desactivación, restablecimiento de contraseña, asignación de vendedor y resultado de intentos de correo de estado de cuenta. Cada fila contiene actor, acción cerrada, tipo/identificador de objetivo, hora y resultado. No guarda hashes, contraseñas, tokens, detalles SQL, cuerpo de correo ni adjuntos.

Para que el dashboard de Daniel pueda consumir la funcionalidad sin acoplarse a controladores:

```java
CollectionDashboard board = services.collectionAssistant().dashboard(session);
board.overdueCommitments();
board.agedDebts();
board.upcomingCommitments();
board.statementsPendingReview();
board.recommendations();
```

`dashboard(session)` vuelve a validar `VIEW_PORTFOLIO`. Los cuatro contadores y las filas deben ocultarse/limpiarse si esa autorización falla; no copiar el detalle de contacto al dashboard. La gestión de perfiles, historial, preparación/revisión/envío de recordatorios requiere `MANAGE_COLLECTION`; auditoría requiere `VIEW_ADMIN_AUDIT`. No se modifica la rama visual de Daniel con este contrato.

## Pedidos a domicilio: pendiente independiente

La entidad `purchase_order` representa pedidos a proveedores y no se reutiliza para entregas a clientes. No hay un agregado de pedidos domiciliarios en este cambio. El contrato mínimo futuro debería incluir pedido y líneas con cantidades/precios acordados, estados y transiciones permitidas (incluido «en camino»), actor/fecha/auditoría, idempotencia, consentimiento y canal autorizado. Solo entonces se podría preparar un aviso ligado a la transición. No existe ni se propone GPS o consulta de ubicación del repartidor.

## Verificación y límites

Las pruebas unitarias comprueban reglas, permisos, consentimiento, PDF, simulación, idempotencia y casos de error. `CustomerCollectionPostgresIntegrationTest` es optativa y exclusiva del laboratorio efímero de CI; requiere V107/V108 y su propiedad de activación. La omisión local sin URL no cuenta como prueba PostgreSQL aprobada. No ejecutar estas migraciones en bases locales existentes como parte de las pruebas.
