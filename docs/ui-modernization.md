# Modernización visual de La Exclusiva — PR #25

## Estado y coordinación

Se integró `origin/develop` en `feature/ui-modernization` mediante merge normal (`4766324`), incorporando íntegramente el PR #24 (`a5c7670`). No hubo conflictos. Se conserva el tema azul/blanco/dorado y el PR continúa como Draft.

`main_menu_modern.fxml` se adaptó al MainController vigente: administración real (`handleShowAdminUsers`), permisos, autoridad de sesión, cierre, revocación y recuperación de logout. Después de verificar administración/navegación y permisos con sesiones auténticas, se aplicó el mismo FXML a `main_menu_view.fxml`. Una regresión comprueba que ambas vistas siguen siendo idénticas. App.java y los controladores no se modifican.

## Observaciones de Carlos

1. **Sincronización y funcionalidades:** el dashboard principal conserva todos los controles/handlers del PR #24. La pantalla administrativa muestra usuarios reales del repositorio de la composición; no vuelve al botón deshabilitado del primer borrador. Las pruebas cubren administración, seis destinos de navegación, sesión sintética rechazada, sesión revocada y recuperación cuando no se puede regresar al login.
2. **Login:** el controlador conserva su estilo rojo inline y sus mensajes. El CSS proporciona al mensaje un fondo opaco `#080c12`: tanto el rojo heredado como el texto claro tienen contraste de texto normal >= 4.5:1. No se intenta ganar la cascada con selectores ni se altera el controlador. El aviso se gestiona visualmente cuando tiene texto; se comprueban campos ausentes, credenciales incorrectas y fallo del servicio, además del borrado de la contraseña.
3. **Identidad de escritorio:** solo se reservan rutas de recursos originales; no hay logos inventados, imágenes generadas ni iconos ficticios. La mascota original azul transparente ya está integrada; sigue faltando el logo independiente aprobado. No se ha integrado ni comprobado el icono de ventana/barra de tareas.
4. **Permisos y ventanas pequeñas:** todos los accesos rápidos reflejan visible/managed/disabled de sus botones de navegación. Las pruebas comparan su disponibilidad con permisos reales de ADMINISTRATOR y WORKER; Clientes, Cartera e Inventario están autorizados para ambos roles actuales. Vendedores, Caja y Administración no aparecen para WORKER. Toda acción sigue revalidándose en los controladores/servicios del PR #24; ocultar controles no sustituye esa autorización. Logout deshabilita también los accesos rápidos. Cartera, Inventario y Login usan ScrollPane; los formularios pueden distribuirse en varias filas y las tablas mantienen una altura mínima legible.

Los indicadores globales, curva semanal y actividad unificada del prototipo siguen pendientes de fuentes públicas del controlador. No se introducen cifras ilustrativas como datos reales ni botones para funciones inexistentes.

## Mascota integrada y recursos pendientes

Estructura preparada bajo `/com/laexclusiva/ui/images/` en el classpath:

- `brand/logo-original.svg` o `.png` y `brand/icons/la-exclusiva-{16,32,48,64,128,256}.png`; ICO `brand/icons/la-exclusiva.ico`.
- `mascot/mascot-blue-transparent.png`: botella original con rostro, corona, proporciones y logo intactos, delantal azul y transparencia real.

El PNG de la mascota fue entregado por el usuario como `Botella Real La Exclusiva.png` e integrado el 23/11/2026. Es una copia exacta de 931 × 1690 px y 1.413.175 bytes, con SHA-256 `cd1baeb2f29aa6631d5fb47622809adc9a38a8a13779bc59fda9aa4616d7a315`. Tiene 816.540 píxeles totalmente transparentes; se inspeccionó sobre azul, blanco y negro sin observar residuos visibles que justificaran retocar el original. No hubo reconstrucción con IA ni cambios de píxeles. Ambos FXML lo muestran directamente sobre el degradado, sin rectángulo, marco o tarjeta opaca; conserva proporción y cuerpo completo. FlowPane mueve la imagen a otra fila al reducir el ancho y el dashboard permite desplazamiento. La integridad del PNG también se comprobó dentro del JAR empaquetado.

Los README describen la procedencia y las rutas. El logo independiente y sus variantes de iconos **todavía no existen** y no se cargan archivos ausentes ni se extrae el logo del delantal. La captura roja y las ediciones generativas descartadas siguen fuera del producto.

Carlos debe coordinar cualquier `stage.getIcons().addAll(...)` en App.java y la configuración del ICO del launcher Windows. Se necesitan los originales antes de preparar sus variantes, y una comprobación real de ventana/barra de tareas antes de declarar esa parte terminada. La [propuesta concreta de integración de escritorio](desktop-identity-integration.md) describe Stage, barra de tareas, acceso directo y futuro instalador; solo se consultó la ayuda de jpackage 17, sin empaquetar ni modificar el launcher.

## Verificación del 23/11/2026

Temurin 17.0.21+9, Maven 3.9.12, JavaFX 17.0.2. Comando local, con variables de destino PostgreSQL retiradas únicamente del proceso:

```text
mvn -o -Dlaexclusiva.test.postgres.users=false -Dlaexclusiva.test.postgres.userSecurityVersion=false -Dlaexclusiva.test.postgres.accountInstance=false clean verify
```

**833 casos: 774 aprobados, 59 integraciones PostgreSQL omitidas, 0 fallos y 0 errores; BUILD SUCCESS.** `git diff --check` también se revisa antes de publicar.

Catorce pruebas de VisualRefreshTest: carga de ocho vistas, navegación/admin real, distribución de Clientes, permisos por rol, rechazo de sesión sintética/revocada, logout y recuperación, paridad entre dashboard principal/alternativo, mensajes de login y contraste, desplazamiento de Cartera/Inventario, integridad y alfa del PNG aprobado, carga desde classpath, proporción y límites del banner a 1280/800/700 px sin recorte ni solapamiento del texto, navegación completa a 800×600 y métricas de formularios/selector a 560×600. Son pruebas automatizadas sobre JavaFX real, no una revisión manual ni validación de PostgreSQL.

En la revisión anterior (`eceb23f`), la primera suite completa detectó diez errores de montaje visual en pruebas antiguas que hacían `lookup()` antes de aplicar CSS a ScrollPane; otra repetición aisló tres casos de Cartera sin Scene. Se añadió Scene/CSS/layout a esos montajes, **sin quitar ni debilitar ninguna aserción de contraseñas, autorización o saldos**. No se modificó código de autenticación ni servicios comerciales.

Capturas reales mediante JavaFX `Node.snapshot`, con datos ficticios únicamente en fixtures memory: [login](ui-review/login.png), [error de login](ui-review/login-error.png), [dashboard](ui-review/dashboard.png), [dashboard compacto](ui-review/dashboard-compact.png), [Clientes compacto](ui-review/customers-compact.png), [Cartera compacta al final del desplazamiento](ui-review/portfolio-compact.png), [Inventario compacto al final del desplazamiento](ui-review/inventory-compact.png), [mascota a 800 px](ui-review/dashboard-mascot-800.png) y [mascota a 700 px](ui-review/dashboard-mascot-700.png). No representan el marco ni la barra de tareas de Windows.

## Pendientes para revisión final

- Usuario/Carlos: logo original independiente y aprobación de sus variantes PNG/ICO; revisión visual de la mascota ya integrada y del conjunto final.
- Carlos: integración Stage/launcher, fuentes de indicadores y recorrido manual coordinado de login, administración y logout.
- El PR permanece en Draft; no se hace merge a develop. No se ejecutan SQL, migraciones ni PostgreSQL en esta actividad.
## Revisión de ventanas compactas y coordinación del PR #26

El 23/11/2026 se verificó que el PR #26 seguía abierto y no fusionado. Se
inspeccionó su lista de archivos y el patch de `portfolio_view.fxml`: Carlos
cambia su contenedor y la altura de la tabla principal. No se integra esa rama,
no se crea un módulo de Compras provisional y no se modifica aquí ninguno de
sus archivos. Los ensayos locales sobre el FXML de Cartera se retiraron; su
contenido se conserva igual al de `8be2051`.

Mejoras comprobables de esta continuación:

- Inventario: se amplían volumen, unidades por canasta, grupo de envase y unidades
  sueltas porque la medición con la fuente real detectó cuatro ayudas recortadas. La CI de Linux detectó además cortes en ID del producto y precio de media canasta; se ampliaron a 155 y 185 px respectivamente, sin debilitar las aserciones.
  El selector de envase pasa de 175 a 250 px para que quepan los tres valores
  del conversor actual, que se conserva sin traducir ni cambiar su lógica.
- Ambas tablas de Inventario reciben altura preferida de 200 px, con mínimo de
  160 y desplazamiento nativo; la tabla de movimientos deja de reservar su gran
  altura por defecto. No se ocultan columnas ni se filtran datos.
- Cartera: una regla CSS específica da 230 px al campo de descripción. No se
  cambia su FXML ni se sobreescribe la altura de tabla que prepara Carlos.
- Dashboard: se comprueba a 800×600 que toda la navegación y logout permanecen
  dentro del lateral. Al pasar esa comprobación, no se cambia su marcado,
  disposición aprobada ni la mascota. Una prueba navega al Inventario real en
  memoria y desplaza horizontalmente su tabla para acceder a las últimas columnas.

Tres nuevas regresiones visuales verifican límites de acciones, ayudas con la
fuente real a 560×600, espacio para todos los tipos de envase y navegación a
800×600. Se mantienen las comprobaciones de roles, administración, logout y
recuperación. Las tablas conservan su comportamiento nativo de redimensionar
columnas y desplazar contenido: no se afirma que todas las columnas o cualquier
texto arbitrariamente largo quepan simultáneamente en una ventana estrecha.
Se descartó un ensayo de ajuste multilínea porque la prueba mostró recorte de
filas; no se publican ese CSS ni renderizadores/controladores nuevos.

Capturas reales de esta revisión: [dashboard 800×600](ui-review/dashboard-800x600.png),
[Inventario 560×600](ui-review/inventory-560x600.png),
[Cartera 560×600](ui-review/portfolio-560x600.png), [formulario de Cartera](ui-review/portfolio-form-560x600.png) e
[Inventario con las últimas columnas visibles](ui-review/inventory-800x600-last-columns.png).
Los datos de las capturas de navegación proceden del catálogo de prueba memory;
no se conectó PostgreSQL ni se usaron datos externos.

Tras la fusión del PR #26 habrá que coordinar el solapamiento previo de ambos PR
en `main_menu_view.fxml` y `portfolio_view.fxml`, conservar el acceso funcional a
Compras y repetir el recorrido compacto de Cartera, compras/recepciones, permisos
y logout. La observación funcional sobre selección de ventas/abonos descrita en
el PR #26 corresponde a su integración y no se presenta como resuelta por estos
ajustes visuales. App.java, launcher, iconos y servicios siguen fuera del alcance.

### Actualización de base durante la validación final

El PR #26 se fusionó **durante esta tarea**, el 23/11/2026 a las 15:12:38 de
America/Bogota, mediante `6e6db096ce0ba044e2890da02a902c302ffea579`.
La comprobación anterior de que estaba abierto correspondía a la inspección
inicial. Se actualizó únicamente `origin/develop` con fetch; no se integró la
rama ni se modificó el árbol de trabajo para resolver conflictos.

La simulación de solo lectura con `git merge-tree` identificó conflictos en:

- `src/main/resources/com/laexclusiva/ui/fxml/main_menu_view.fxml`.
- `src/main/resources/com/laexclusiva/ui/fxml/portfolio_view.fxml`.
- `src/test/java/com/laexclusiva/service/AuthenticationServiceTest.java`.

Estos solapamientos provienen de las integraciones previas de ambos PR, no de
los ajustes compactos de `7100748`. Resolverlos requiere coordinación con Carlos
para conservar Compras, el contenedor/tabla de Cartera y las regresiones de
sesión. No se hizo merge ni se resolvieron automáticamente archivos compartidos.
El PR #25 continúa en Draft y no está listo para merge mientras persista ese bloqueo.

La CI del push `7100748` ejecutó 833 casos y falló en la prueba de métricas por
los dos textos de Inventario indicados arriba (1 caso con error, 59 omitidos).
La corrección conserva las pruebas estrictas y solo aumenta esos dos anchos;
la CI posterior se informa con su ejecución real, sin atribuir éxito a la
integración del PR mientras su base siga en conflicto.

## Integración autorizada del PR #26 — 23/11/2026

El bloqueo descrito en la sección anterior queda resuelto mediante un merge normal
 de `origin/develop` (`6e6db09`) sobre `bc90f12`, autorizado expresamente en esta
continuación. Se conservan `7100748` y `bc90f12`, sin rebase ni sustitución del menú.

Resolución de los tres conflictos:

- `main_menu_view.fxml`: se conserva el dashboard moderno completo y se añade
  `btnPurchases`, `#handleShowPurchases` y «Compras y Recepción». El acceso rápido
  enlaza visibilidad, gestión y deshabilitación al botón real. Ambos menús FXML
  quedan idénticos, comprobados mediante prueba. Se reduce solo el espaciado del
  lateral para mantener diez destinos/logout accesibles a 800×600.
- `portfolio_view.fxml`: ScrollPane vertical, tabla de cartera de 150 px y contenido
  con altura mínima preferida, conservando el arreglo de Carlos, los formularios
  adaptables, IDs, eventos y tema visual. Las ventas pendientes son seleccionables
  y el formulario de abonos se alcanza mediante desplazamiento.
- `AuthenticationServiceTest.java`: se conservan las regresiones de ambas ramas,
  el acceso al contenido del ScrollPane y la aplicación de CSS/layout. Se amplía
  el recorrido real login → Compras → administración/clientes → logout → trabajador,
  verificando los permisos y el retorno al dashboard. La primera ejecución dirigida
  detectó un selector de bienvenida consultado después de abandonar el dashboard;
  se movió esa aserción antes de navegar, conservando su comprobación al regresar.

MainController, ApplicationServices y los servicios de Compras se conservan
exactamente como en develop. No se reescriben sus permisos: VIEW_INVENTORY permite
consultar; MANAGE_INVENTORY_ENTRIES gobierna modificaciones. El trabajador puede
consultar pedidos, con editores y recepción deshabilitados; el servicio también
rechaza su escritura. Se mantienen administración, revocación, logout y recuperación.
Los cambios de SQL, workflow y bitácora del líder que figuran en el merge son los
heredados de develop; no son ediciones nuevas de esta modernización.

Validación local: Temurin 17.0.21+9, Maven offline `clean verify`, con destinos
PostgreSQL retirados del proceso y pruebas opt-in deshabilitadas: **839 casos,
779 aprobados, 60 PostgreSQL omitidos, 0 fallos y 0 errores**; BUILD SUCCESS.
Incluye 16 pruebas visuales. Las dos nuevas ejercitan Compras/Recepción con ambos
roles y Cartera con venta pendiente y abonos a 1280×800, 1024×768 y 800×600.
La recepción aumenta una unidad y genera un movimiento; el abono actualiza saldos
sin cambiar existencias. Se conserva la regresión de Carlos de reintento de abono
tras respuesta perdida. No se ejecutó SQL, migraciones ni PostgreSQL.

Capturas reales de JavaFX (escenas renderizadas por pruebas, no recorrido manual):

- [Dashboard 1280×800](ui-review/integrated-dashboard-ADMINISTRATOR-1280.png) y
  [800×600](ui-review/integrated-dashboard-ADMINISTRATOR-800.png), con la mascota
  original transparente, sin modificar ni reconstruir su imagen.
- Compras: [1280×800](ui-review/purchases-ADMINISTRATOR-1280.png),
  [1024×768](ui-review/purchases-ADMINISTRATOR-1024.png),
  [800×600](ui-review/purchases-ADMINISTRATOR-800.png).
- [Trabajador en consulta](ui-review/purchases-WORKER-800.png) y
  [formulario de recepción tras desplazarse](ui-review/receipts-ADMINISTRATOR-800.png).
- Venta pendiente seleccionada y abono: [1280×800](ui-review/portfolio-payment-1280.png),
  [1024×768](ui-review/portfolio-payment-1024.png),
  [800×600](ui-review/portfolio-payment-800.png).

Los fixtures «QA visual» son ficticios y exclusivos del modo memory; no se presentan
como cifras comerciales. Las tablas estrechas conservan su desplazamiento nativo;
no se afirma que todas las columnas quepan simultáneamente. La captura de recepción
se toma antes de confirmar; la prueba verifica después el historial y las existencias.

El PR #25 se mantiene Draft para revisión. Siguen pendientes el logo independiente,
la integración de identidad de escritorio por Carlos y las fuentes reales de indicadores
globales. No se modifica App.java, launcher ni iconos. El resultado de CI del nuevo
commit se comprobará tras publicar; las pruebas PostgreSQL omitidas no se declaran aprobadas.
### Corrección de ancho tras CI Ubuntu

La primera CI del merge (`45261680900` y `45261691421`) detectó que la fuente de
Ubuntu recortaba «Compras y Recepción»: 839 casos, 1 error, 60 omitidos. Se conserva
la aserción de medidas y se amplía el lateral de 220 a 256 px en ambos menús,
sin modificar controladores o permisos. Se repitió Maven offline clean verify con
Temurin 17.0.21+9: 839 casos, 779 aprobados, 60 omitidos y cero fallos/errores.
Las capturas integradas se renovaron con este ancho. La ejecución local incluyó
una pausa prolongada del equipo; su duración no se utiliza como medida de rendimiento.
## Revisión independiente de accesibilidad — 23/11/2026

Se revisó el PR #27: continúa abierto y no fusionado. No se integra su rama ni se
preparan accesos provisionales de Alertas; el trabajo parte del HEAD a0c7126 del PR #25.

Hallazgos medidos y correcciones limitadas:

- El extremo claro del banner (#0587ef) tenía contraste 3,67:1 con texto blanco
  normal. Se cambia solo ese extremo a #086bdb: contraste mínimo 5,08:1. Se mantienen
  azul profundo, blanco y dorado, distribución del banner y mascota original intacta.
- El borde dorado genérico de foco contrastaba 1,74:1 con la tarjeta blanca. Se usa
  azul profundo #102b56: 13,98:1 sobre blanco y 12,37:1 sobre el acceso rápido claro.
  El dorado del menú lateral, su foco específico y la identidad visual se conservan.
- Usuario y Contraseña reciben nombres accesibles explícitos en FXML. No cambian
  fx:id, eventos, validaciones, secretos, controlador o autenticación.

Las nuevas pruebas primero detectaron el contraste insuficiente y la ausencia de
nombres accesibles explícitos (18 casos, dos errores agregados). Se corrigieron
los recursos y la repetición dirigida pasó 18/18. Se comprobó navegación real del
motor JavaFX con eventos Tab/MAYÚS+Tab: Usuario → Contraseña → Iniciar sesión y
retorno a Contraseña, manteniendo el botón predeterminado. Se mide el foco aplicado
por CSS y se comprueba que deshabilitar el botón reduce su opacidad, cambia el cursor
y que habilitarlo restaura su apariencia. No se presenta como certificación completa
con lector de pantalla ni como evaluación de accesibilidad de todas las rutas.

Inventario, Cartera y ahora Compras se comprueban a 560×600: ayudas completas de
campos y acciones dentro del ancho disponible. Las pruebas no detectaron necesidad
de cambiar su disposición; se conservan scrolling vertical y horizontal nativos,
ventas pendientes seleccionables y abonos accesibles. No se elimina ninguna columna,
prueba o funcionalidad para obtener estos resultados. El lateral mantiene Compras,
administración y logout a 800×600; las tarjetas y la mascota conservan su distribución.

Validación final local con Temurin 17.0.21+9, `mvn -o clean verify`: **841 casos,
781 aprobados, 60 PostgreSQL omitidos, 0 fallos y 0 errores**, BUILD SUCCESS.
Incluye 18 pruebas visuales y los recorridos ya existentes a 1280×800, 1024×768 y
800×600, permisos de ambos roles, Inventario/movimientos, Compras/recepción,
Cartera/abonos, administración, login/logout y recuperación. Sin SQL ni conexiones.

Capturas nuevas y actualizadas de JavaFX:

- [Banner y foco a 1024×768](ui-review/dashboard-accessibility-1024.png): muestra
  de CSS/FXML aislada, sin sesión; el pseudoestado de foco se aplica expresamente
  para medir su borde. No representa una sesión autenticada.
- Dashboard con sesión auténtica de prueba: [1280×800](ui-review/integrated-dashboard-ADMINISTRATOR-1280.png)
  y [800×600](ui-review/integrated-dashboard-ADMINISTRATOR-800.png).
- [Login con foco mediante teclado](ui-review/login-keyboard-560x600.png) y
  [botón deshabilitado](ui-review/login-disabled-560x600.png).
- [Formulario de Compras 560×600](ui-review/purchases-560x600.png), cargado sin
  sesión para inspección de disposición: los editores aparecen deshabilitados.
  Las pruebas de navegación anteriores comprueban separadamente ambos roles.

No se modifican Java de producción, App.java, launcher, iconos, servicios, SQL,
migraciones, permisos ni el archivo original de mascota. Siguen pendientes el logo
independiente y la integración de escritorio coordinada con Carlos. El PR #25 sigue
Draft; la CI del nuevo commit se comprobará después de publicar.
### Hallazgo adicional de CI: ayuda tributaria en Compras

Las dos ejecuciones de Ubuntu del commit f278ee1 detectaron un único recorte en
supplierTaxId al ampliar la prueba compacta a Compras (841 casos, 1 error, 60
omitidos; run 45291073097). Se mantiene la aserción estricta y se reserva un ancho
preferido de 210 px mediante CSS, sin cambiar FXML de Compras ni su controlador.
El FlowPane redistribuye los campos; las capturas de Compras se renovaron.
La repetición local de `mvn -o clean verify` con Temurin 17.0.21+9 pasa 841 casos:
781 aprobados, 60 PostgreSQL omitidos y cero fallos/errores, incluidas 18 visuales.
Este hallazgo amplía las correcciones anteriores: la revisión local de Windows
no había reproducido ese recorte de fuente. La CI de la corrección se comprobará
tras publicar, sin presentar los resultados anteriores fallidos como aprobados.
## Ajuste local tras revisión personal — mascota al extremo derecho

Cambio solicitado el 23/11/2026, pendiente de aprobación visual antes de publicar.
Ambos menús mantienen marcado idéntico. El banner cambia de FlowPane a HBox:
la columna de bienvenida crece para ocupar el espacio disponible y la mascota
queda en el extremo derecho con el padding de 28 px del banner. No hay posiciones
absolutas. El ImageView conserva proporciones, limita su altura a 300 px y su ancho
al 22 % del banner, por lo que se reduce en ventanas pequeñas sin cubrir el texto.
No se modifica el PNG original ni las acciones, permisos o controladores.

La prueba de mascota conserva las comprobaciones de integridad, proporción,
ausencia de recorte y solapamiento; añade margen derecho y reducción proporcional.
Renderiza 1280×800, 1024×768, 800×600, 700×600 y el área de trabajo detectada de
1536×816 unidades lógicas. Esta última escena es una prueba de distribución al
ancho disponible, no una captura de una ventana nativa maximizada.

Capturas reales de escenas JavaFX:

- [1280×800](ui-review/dashboard-mascot-right-1280x800.png).
- [1024×768](ui-review/dashboard-mascot-right-1024x768.png).
- [800×600](ui-review/dashboard-mascot-right-800x600.png).
- [Área de trabajo 1536×816](ui-review/dashboard-mascot-right-1536x816.png).

Temurin 17.0.21+9: pruebas dirigidas 18/18; `mvn -o clean verify`: **841 casos,
781 aprobados, 60 PostgreSQL omitidos, 0 fallos y 0 errores**. Sin SQL ni migraciones.
La aplicación real se volvió a abrir en Windows y se comprobaron visibilidad y
estado nativo maximizado. Para dejar el dashboard listo se utilizó un arranque
local temporal en target, ignorado por Git, que llama App y envía credenciales demo
al formulario de login real; no se sustituye ni se modifica autenticación. Las
credenciales se muestran en un cuadro local y no se guardan en archivos o registros.
Una tentativa de captura de escritorio quedó obstruida por otra ventana y no se
incluye como evidencia; las imágenes enlazadas son escenas JavaFX de las pruebas.

**Animación pendiente:** solo existe el PNG estático aprobado. No se dispone de una
secuencia aprobada que conserve el diseño exacto durante el gesto de beber; no se
ha deformado ni generado otro personaje. [Especificación del recurso necesario](mascot-animation-resource.md):
PNG RGBA o GIF transparente de 4–6 s, inicio/final en reposo, ciclo de unos 25 s,
alternativa estática y futura opción de reducir/desactivar movimiento. No se añade
un control de animación sin funcionalidad real. El ciclo de vida visual deberá
coordinarse si requiere integrar MainController.

No se integró el PR #27, no hubo merge, commit ni push. El PR #25 permanece Draft.
Los cambios locales esperan la aprobación visual del usuario.
## Ajustes visuales compactos aprobados para publicación

El usuario autorizó separar la modernización de la herramienta de demostración.
Este grupo conserva la alineación derecha de la mascota y adapta únicamente la
presentación: etiquetas persistentes en campos numéricos, columnas sin recorte,
estados/envases/roles en español y orden inicial de Clientes por ID. Ventas, Caja
y Usuarios conservan acceso a sus formularios e historiales mediante desplazamiento.
Las acciones, cálculos, permisos y servicios no cambian. La animación de sorbo
continúa pendiente de un recurso gráfico aprobado; no se deforma el PNG original.

Las pruebas existentes conservan sus aserciones de seguridad y negocio; solo se
adaptan la búsqueda dentro de ScrollPane, el pase de CSS/layout y textos traducidos.
Este commit no incorpora carga de datos ni recursos de demostración. La herramienta
queda para el siguiente commit independiente. No se integra el PR #27 ni se hace merge.
Validación independiente del índice visual exportado: Temurin 17.0.21+9, Maven offline clean verify, 841 casos; 781 aprobados, 60 PostgreSQL omitidos y cero fallos/errores. La carga y las pruebas de la nueva demo no formaron parte de esa copia.
