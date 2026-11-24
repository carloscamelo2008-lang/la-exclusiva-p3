# Demostración interactiva completa — solo memoria

Todos los nombres, contactos, direcciones, identificaciones tributarias e importes
de esta carga son ficticios. Los teléfonos tienen formato móvil colombiano; no se
afirma que sean números disponibles y no deben utilizarse para contactar a nadie.
No hay SQL, migraciones, correo ni conexiones a bases de datos externas.

## Iniciar o regenerar

Desde PowerShell en la carpeta del proyecto, con la aplicación anterior cerrada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-interactive-demo.ps1
```

Bypass se limita a ese proceso, sin cambiar la configuración permanente. El script
usa Temurin 17.0.21+9 y Maven local/offline, compila la herramienta de revisión y
genera contraseñas temporales nuevas. No instala dependencias ni software. Rechaza
un segundo arranque si encuentra una ventana de La Exclusiva abierta.

Se abre el dashboard maximizado como administrador, pasando las credenciales por
el formulario de login real. El cuadro «credenciales de demostración completa»
permite copiarlas con Ctrl+C: minimizarlo para conservarlo durante la revisión.
No se guardan contraseñas en archivos, argumentos Java ni registros. Las cuatro
cuentas de trabajador comparten una contraseña aleatoria exclusiva de esa ejecución;
son cuentas ficticias de laboratorio, nunca una configuración de producción.

| Usuario | Estado | Vendedor | Qué permite comprobar |
|---|---|---|---|
| admin | Activo, administrador | Sin asignar | Todas las operaciones ya disponibles y administración |
| elena.torres | Activo, trabajador | Elena Torres | Venta con vendedor asignado y permisos limitados |
| andres.molina | Activo, trabajador | Andrés Molina | Otra cuenta y vendedor exclusivo |
| operador | Activo, trabajador | Sin asignar | Consultas y rechazo de venta sin vendedor |
| paula.serrano | Inactivo, trabajador | Paula Serrano | Login rechazado y posterior reactivación administrativa |

Las contraseñas se someten a PasswordPolicy y PBKDF2 existentes. Los cambios de rol,
estado, contraseña y vendedor siguen invalidando sesiones. No se permite retirar
al último administrador: crear primero otro administrador si se quiere probar ese
cambio. El cuadro muestra las contraseñas **iniciales**; no se actualiza si el usuario
las cambia durante la prueba. Al cerrar la aplicación, el script cierra su cuadro.

## Configuración editable

Archivo UTF-8: `src/test/resources/com/laexclusiva/demo/interactive-demo.properties`.
El separador de campos es `|`; sus comentarios describen cada columna. No incluir
contraseñas ni datos personales reales. Se pueden modificar:

- Nombres, teléfonos y direcciones de los 15 clientes y cinco vendedores.
- Proveedores, identificaciones tributarias y contactos ficticios.
- Existencias iniciales por producto: canastas y unidades sueltas, incluido cero.
- Ocho referencias demo adicionales: nombre, volumen, capacidad, tipo de envase y
  precios de unidad, media canasta y canasta completa.
- Distribución de ventas por vendedor, cantidades de compras, deuda general y
  movimientos manuales de caja. Las validaciones de los servicios siguen vigentes;
  al reducir existencias hay que mantener suficiente mercancía para las ventas.

Las seis referencias del catálogo original se reutilizan sin modificar sus precios,
capacidades o identidades. ProductService permite crear productos, pero no expone
una operación de modificación de precios existentes; esta demo no escribe directamente
en el repositorio para eludir esa limitación. Los precios configurables corresponden
a las ocho referencias añadidas. Se conservan las tres presentaciones reales del modelo.

También puede leerse una copia externa de la configuración:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-interactive-demo.ps1 -Configuration "C:\ruta\demo-local.properties"
```

Cada arranque construye una composición nueva y publica sus servicios solo cuando
terminan la carga y la conciliación. No agrega sobre datos previos ni usa objetos
visuales como almacenamiento. No se modifica App.java, Main.java, el launcher
habitual, ApplicationServices o los contratos comerciales. La herramienta separada
InteractiveDemoReview utiliza los FXML y controladores reales y conserva la misma
composición al cerrar sesión y volver al login. No se integra el PR #27.

## Datos y recorrido manual

- **Clientes:** 15, IDs `CLI-001` a `CLI-015`. Clientes con crédito y abonos,
  deudas generales y clientes sin saldo. `CLI-012` tiene deuda general pendiente;
  `CLI-011` terminó de pagar su deuda general; 013–015 empiezan sin deuda.
- **Vendedores:** cinco, con 12, 8, 5, 3 y 2 ventas respectivamente.
- **Inventario:** 14 referencias, incluidas todas las marcas solicitadas. Niveles
  altos y medios, Andina con tres unidades, Heineken con seis y Budweiser/Colombiana
  agotadas. Usar Registrar entrada o recibir pedidos para reponerlas. Las entradas
  iniciales, recepciones y ventas generan movimientos por sus servicios reales.
- **Compras:** cinco proveedores y diez pedidos. Cuatro completos, tres parciales,
  dos pendientes y uno cancelado. Las siete recepciones ya efectuadas incrementan
  existencias; quedan cinco pedidos abiertos para recibir manualmente. Al consultar
  el detalle, seleccionar una línea, ingresar cantidades y añadirla antes de confirmar.
- **Ventas:** 30, de contado/crédito, abonos parciales y pagos completos; diez con
  varios productos, usando unidades, medias canastas y canastas. Queda mercancía para
  vender. Agregar líneas al carrito, elegir cliente si hay crédito y vendedor si se
  opera como administrador; el trabajador utiliza su asignación vigente.
- **Cartera:** seleccionar un cliente y una venta pendiente antes de registrar un
  abono. Los cargos/pagos de deuda general están separados de los de ventas.
- **Caja:** pagos iniciales y abonos de ventas, tres pagos de deuda general y doce
  movimientos manuales de otros ingresos, transporte, servicios e insumos operativos.
  Los recibos comerciales se derivan automáticamente: no se agregan cobros duplicados.
  El filtro predeterminado puede mostrar solo hoy; elegir desde hace 29 días hasta
  hoy para ver todo el historial manual.
- **Usuarios:** seleccionar una cuenta antes de cambiar rol, estado, contraseña o
  vendedor. No asignar a dos cuentas activas el mismo vendedor. Cambiar un campo de
  seguridad revoca sesiones anteriores; no se renuevan automáticamente.

Las ventas y sus pagos conservan la **fecha real del registro**, decisión confirmada
por el usuario: sus APIs no aceptan una fecha histórica. Tampoco se alteran el reloj
de Windows ni los repositorios para aparentar historia. Los doce movimientos manuales
de caja sí utilizan su API de fecha y se distribuyen en los últimos 30 días. La deuda
general usa igualmente la fecha real. Si se necesitan ventas históricas, queda pendiente
coordinar con Carlos una extensión de reloj inyectable; no está implementada.

## Funcionalidades que todavía no existen en las pantallas

Esta carga no añade operaciones comerciales nuevas. Los servicios/pantallas actuales
no ofrecen edición o eliminación de clientes/vendedores, modificación o eliminación
de productos existentes, ni edición/eliminación de ventas registradas. Compras permite
alta/activación de proveedores y pedido/recepción; el servicio admite cancelar pedidos,
pero la pantalla actual no contiene una acción de cancelación. No se agregan botones
que aparenten esas funciones. La administración ofrece las operaciones existentes,
sin implementar borrado físico. Los indicadores globales del dashboard siguen pendientes
de su integración funcional; no se sustituyen por cifras ficticias.

## Integridad y conservación de cambios

InteractiveDemoData comprueba cada recepción y descuento de venta; concilia todo el
inventario contra entradas/salidas, saldo agregado contra deuda de ventas más deuda
general, y caja contra pagos efectivos y referencias únicas. Las pruebas ejercitan
operaciones nuevas después de la carga, sin duplicaciones o permisos artificiales.
Un fallo descarta la nueva composición sin publicarla en la interfaz.

Los cambios manuales viven **solo mientras esta ejecución permanezca abierta**.
Cerrar o reiniciar destruye los repositorios en memoria. Volver a ejecutar el script
reconstruye el escenario, pero no recupera modificaciones realizadas en la ventana.
Una configuración editable define la carga inicial; no es una copia de seguridad.

Para conservar modificaciones entre sesiones haría falta aprobar primero una opción
separada: instantáneas locales versionadas con escritura atómica, restauración que
valide relaciones/historial e identidad, y protección de credenciales; o una base
local aislada expresamente autorizada. Ninguna se implementa ni se conecta en esta
tarea. No se propone serializar tablas visibles ni guardar contraseñas en texto plano.

## Ajustes de presentación

- Anchos suficientes para «Identificación tributaria» y «Cantidad».
- Envases, movimientos de inventario, roles y estados de compra con etiquetas en
  español; los enums, IDs y estados internos permanecen intactos.
- Etiquetas persistentes para canastas, unidades sueltas, cantidad y pago inicial:
  no dependen de un prompt oculto al mostrarse cero.
- Ventas y Caja con ScrollPane e historiales de altura útil. Administración distribuye
  acciones en filas y mantiene desplazamiento para evitar botones cortados.
- Mascota original y alineación al extremo derecho conservadas. Animación pendiente
  del recurso aprobado descrito en `mascot-animation-resource.md`.

Los cambios quedan locales hasta aprobación visual: sin commit, push o merge.

## Revisión de datos naturales y validación local

La carga fue revisada para que las etiquetas de entorno no contaminen los registros:
clientes CLI-001…CLI-015, referencias AGUILA_330/CLUB_COLOMBIA_330/etc., personas sin
sufijos de demostración, direcciones convencionales y proveedores con nombres
comerciales construidos para este escenario, sin copiar fichas de empresas reales.
Notas de pedidos, recepciones, inventario, cargos, abonos y caja utilizan lenguaje
comercial. Los datos siguen siendo sintéticos; no deben utilizarse para contactar
personas, emitir documentos reales o registrar operaciones productivas.

El indicador «Modo demostración» se conserva en el título de Windows y en un pie
discreto, solo dentro de la herramienta de revisión. El pie mantiene el Label real
de mensajes de navegación. No se añade esa marca a datos ni se modifica App.java.
Clientes se ordena por ID ascendente desde TableView; no se altera el repositorio.
Los IDs y claves se generan juntos al crear el escenario para mantener todas las
relaciones; no se renombra únicamente la columna visual ni se dejan referencias viejas.

La primera carga completa pasó 849 casos (789 aprobados, 60 PostgreSQL omitidos).
Después de la revisión de realismo, las pruebas dirigidas pasan 10/10 y la suite
final `mvn -o clean verify`, Temurin 17.0.21+9, pasa **851 casos: 791 aprobados,
60 PostgreSQL omitidos, 0 fallos y 0 errores**. Comprende las 18 visuales previas,
siete verificaciones de datos, dos recorridos de módulos cargados y una del indicador.
Se verifican ventanas 1280×800, 1024×768 y 800×600 y campos a 560×600.

La prueba de contenido recorre nombres/IDs/direcciones, proveedores, productos,
notas de compras y recepciones, movimientos de inventario, deuda general, caja y
usuarios, rechazando los marcadores artificiales en el escenario inicial. También
comprueba enlaces de cada venta y la secuencia de IDs en la tabla de Clientes.
La conciliación sigue dando 63 movimientos de inventario y 51 de caja. El reporte
local de arranque está en target/interactive-demo-report.txt, sin credenciales.

Importes del escenario, calculados por los servicios y no inyectados en etiquetas:

| Concepto | COP |
|---|---:|
| Ventas facturadas | 1.476.500 |
| Cobros de ventas | 859.550 |
| Pagos de deuda general | 165.000 |
| Otros ingresos | 135.000 |
| Egresos | 162.000 |
| Caja neta | 997.550 |

Capturas de escenas reales JavaFX con la carga final (no son datos superpuestos):

- [Clientes ordenados](ui-review/demo-btnCustomers-1280.png) y [800 px](ui-review/demo-btnCustomers-800.png).
- [Vendedores](ui-review/demo-btnSellers-1280.png), [Inventario](ui-review/demo-btnInventory-1280.png),
  [Compras](ui-review/demo-btnPurchases-1280.png), [Usuarios](ui-review/demo-btnAdminUsers-800.png).
- [Historial de Ventas](ui-review/demo-history-btnSales-800.png),
  [historial de Caja](ui-review/demo-history-btnCash-800.png), [Cartera](ui-review/demo-portfolio-800.png).
- Campos con etiquetas persistentes: [Inventario](ui-review/demo-labelled-inventory-560.png)
  y [Ventas](ui-review/demo-labelled-sales-560.png).

La aplicación real se abrió en el escritorio de Windows; se verificaron ventana
visible, maximizada y título «La Exclusiva - Menú Principal · Modo demostración».
No se ejecutaron integraciones PostgreSQL, SQL o migraciones. Sin commit, push,
merge ni integración del PR #27; PR #25 sigue Draft. Cambios pendientes de aprobación.
## Auditoría técnica previa a publicación — 23/11/2026

Aislamiento reforzado: InteractiveDemoData se ubica en src/test/java y la configuración
editable en src/test/resources. La herramienta y sus pruebas ya están en src/test.
Tras Maven clean verify se inspeccionó el JAR normal: no incluye InteractiveDemoData,
InteractiveDemoReview ni interactive-demo.properties. Sí están en target/test-classes
para la ejecución explícita mediante el script. No se modifica pom.xml ni su mainClass.
El inicializador demo antiguo permanece intacto y separado de esta carga completa.

No hay referencias a la nueva carga en src/main, el arranque normal o workflows.
El script exige memoria y no acepta un proveedor JDBC; el cargador construye siempre
ApplicationServices.create("memory", Map.of()). El verificador rechaza una composición
PostgreSQL antes de consultar registros. Las pruebas reales PostgreSQL siguen omitidas.

Las contraseñas de ejecución se generan aleatoriamente en el proceso; no se guardan
en configuración, documentación, argumentos Java ni reportes. Las nuevas pruebas
usan valores generados en memoria, sin credenciales literales utilizables. Los valores
sintéticos preexistentes de regresiones y la lista de bloqueo de contraseñas no son
credenciales de una instalación ni se utilizan como contraseñas predeterminadas.

Repetición auditada: Temurin 17.0.21+9, Maven 3.9.12 offline clean verify, 851 casos:
791 aprobados, 60 PostgreSQL omitidos, 0 fallos y 0 errores. git diff --check sin
problemas; también se revisan los archivos nuevos sin añadirlos al staging.
La aplicación que ya estaba abierta no se cierra ni se reinicia durante la auditoría.

El inventario real contiene 40 archivos pendientes, no 20: 25 de texto/código y
15 capturas. La separación propuesta para aprobación consta de 19 exclusivos de
modernización, 19 exclusivos de demo y dos documentos compartidos por secciones.
El detalle se registra en docs/ui-modernization.md. No se crea ningún commit ni push.