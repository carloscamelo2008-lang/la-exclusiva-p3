# Modernización visual de La Exclusiva

## Alcance aplicado

Tema JavaFX 17 centralizado en `theme.css`: azul profundo/brillante, blanco y dorado, campos, tablas, botones y estados hover, focus, pressed y disabled. `navigation.css` importa el tema y conserva la pseudoclase `active` del controlador.

Login, Clientes, Vendedores, Inventario, Ventas, Cartera, Caja y Compras cargan el tema. Los cambios de Compras se limitan a asociar el CSS en su FXML; no se modifica su implementación. Se conservan todos los `fx:id`, `onAction`, `fx:controller` y tipos de controles inyectados. Los formularios adaptables usan FlowPane; los HBox inyectados se conservan como envoltorios.

`main_menu_modern.fxml` es una alternativa funcional para revisión, con el MainController existente, nombre/rol de la sesión, navegación, accesos rápidos y desplazamiento. Los accesos restringidos siguen la visibilidad de los botones originales; la autorización sigue en los controladores. No añade operaciones ni cifras ilustrativas. Los historiales y valores siguen consultándose en sus módulos reales.

## Coordinación obligatoria con Carlos

El fetch del 23/11/2026 confirmó develop en `cfdc1812c40a0fd690a88542296017784f0e5c67` y la integración de Carlos en `origin/feature/identity-app-integration` (`110b50c`), que modifica `main_menu_view.fxml`, controladores, composición y servicios. Ninguno de esos archivos se modifica aquí.

**El dashboard alternativo no reemplaza todavía el dashboard de inicio de la aplicación.** Carlos debe trasladar su integración más reciente al FXML alternativo o incorporar su estructura visual en el FXML principal después de revisar los contratos nuevos. No basta con cambiar la ruta si su MainController ya requiere controles adicionales. Este PR no integra ramas ni altera la navegación principal.

Los indicadores globales, curva semanal, perfil desplegable y actividad unificada del prototipo necesitan fuentes y contratos que no expone el MainController de esta base. No se dibujan datos ficticios ni botones que aparenten esas funciones. Gestión de Usuarios permanece deshabilitada según el comportamiento actual de develop, sin sustituir la integración de Carlos.

Las filas de formularios se distribuyen al reducir el ancho; el dashboard alternativo y Compras tienen desplazamiento. Las pantallas densas de Cartera/Inventario conservan su estructura y requieren altura suficiente para todos sus bloques: no se garantiza aún su uso completo en ventanas pequeñas. Una transformación global de su contenedor debe coordinarse con los controladores y sus pruebas.

## Mascota y recursos originales

La captura original proporcionada mide 291 × 528 px, tiene delantal rojo y fondo fotográfico. Dos ensayos generativos fueron descartados porque cambiaban detalles del personaje. Tras reiterar que la mascota debe llevar delantal azul e integrarse sin un rectángulo de fondo, el usuario eligió **esperar al original azul con transparencia**, en vez de autorizar recorte/recoloración local.

No se incluye la captura roja ni ninguno de los resultados generados. El banner queda preparado para recibir el PNG original aprobado como imagen sobre su degradado, sin marco ni tarjeta y conservando sus proporciones. La mascota y la fidelidad completa del banner siguen pendientes de ese recurso. No se afirma que el diseño final esté terminado.

## Identidad de escritorio

No se recibió un logo original independiente: la segunda ruta entregada corresponde a la misma captura de la mascota. No se inventa ni se reconstruye el logo a partir del delantal. Las variantes PNG 16/32/48/64/128/256 y el ICO multirresolución quedan pendientes de ese archivo original.

App.java está reservado a Carlos. Una vez disponibles las variantes, la integración mínima consiste en cargar desde el classpath los PNG con `stage.getIcons().addAll(...)` en cada Stage relevante, antes de mostrarlo. Para Windows empaquetado debe configurarse el ICO en el launcher de jpackage, además de los iconos JavaFX; eso requiere revisar el empaquetado acordado. Esta propuesta no está aplicada. **No se ha corregido ni verificado el icono de ventana o barra de tareas.**

Todos los recursos integrados usan rutas relativas al classpath Maven; ninguna ruta personal aparece en los FXML.

## Verificación real del 23/11/2026

- Temurin 17.0.21+9, Maven 3.9.12, JavaFX 17.0.2.
- `mvn -o -Dlaexclusiva.test.postgres.users=false -Dlaexclusiva.test.postgres.userSecurityVersion=false -Dlaexclusiva.test.postgres.accountInstance=false verify`: **805 ejecutadas, 750 aprobadas, 55 omitidas PostgreSQL, 0 fallos y 0 errores; BUILD SUCCESS**.
- Tres pruebas nuevas: carga y render de ocho FXML con sus controladores/CSS; navegación real del dashboard hacia seis módulos y regreso; distribución del formulario de Clientes a 600 px sin cambiar su HBox inyectado.
- Capturas obtenidas con JavaFX `Node.snapshot`, sin mockups ni cifras añadidas: [login](ui-review/login.png), [dashboard alternativo](ui-review/dashboard.png), [dashboard compacto](ui-review/dashboard-compact.png), [clientes compacto](ui-review/customers-compact.png). Son renders reales del contenido JavaFX, no capturas del marco o barra de tareas de Windows.
- Se abrió además una ventana de revisión con Java 17 y servicios exclusivamente en memoria; `Stage.isShowing()` confirmó `true`. El lanzador fue temporal y no se incorpora al código del producto. No se probó aquí la autenticación integrada que desarrolla Carlos.
- El intento adicional de `clean verify` se detuvo en la limpieza por registros bloqueados de una aplicación ya abierta; no fue un fallo de pruebas. No se cerró esa aplicación. Se retiró únicamente la copia descartada de la imagen en target y se repitió `verify`; el empaquetado se inspecciona sin esa imagen.
- Sin SQL, PostgreSQL, migraciones ni cambios en controladores, servicios, persistencia o composición.

## Revisión pendiente

Carlos: integración del dashboard y sus fuentes actuales; Stage/launcher y coordinación con su rama. Usuario: logo original independiente y PNG original azul con transparencia, según su decisión expresa de esperar ese recurso. La fidelidad completa al prototipo y la identidad de escritorio no se declaran terminadas.