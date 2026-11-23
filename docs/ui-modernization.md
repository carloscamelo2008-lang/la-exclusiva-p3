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
