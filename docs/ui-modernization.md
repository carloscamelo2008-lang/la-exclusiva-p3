# Modernización visual de La Exclusiva — PR #25

## Estado y coordinación

Se integró `origin/develop` en `feature/ui-modernization` mediante merge normal (`4766324`), incorporando íntegramente el PR #24 (`a5c7670`). No hubo conflictos. Se conserva el tema azul/blanco/dorado y el PR continúa como Draft.

`main_menu_modern.fxml` se adaptó al MainController vigente: administración real (`handleShowAdminUsers`), permisos, autoridad de sesión, cierre, revocación y recuperación de logout. Después de verificar administración/navegación y permisos con sesiones auténticas, se aplicó el mismo FXML a `main_menu_view.fxml`. Una regresión comprueba que ambas vistas siguen siendo idénticas. App.java y los controladores no se modifican.

## Observaciones de Carlos

1. **Sincronización y funcionalidades:** el dashboard principal conserva todos los controles/handlers del PR #24. La pantalla administrativa muestra usuarios reales del repositorio de la composición; no vuelve al botón deshabilitado del primer borrador. Las pruebas cubren administración, seis destinos de navegación, sesión sintética rechazada, sesión revocada y recuperación cuando no se puede regresar al login.
2. **Login:** el controlador conserva su estilo rojo inline y sus mensajes. El CSS proporciona al mensaje un fondo opaco `#080c12`: tanto el rojo heredado como el texto claro tienen contraste de texto normal >= 4.5:1. No se intenta ganar la cascada con selectores ni se altera el controlador. El aviso se gestiona visualmente cuando tiene texto; se comprueban campos ausentes, credenciales incorrectas y fallo del servicio, además del borrado de la contraseña.
3. **Identidad de escritorio:** solo se reservan rutas de recursos originales; no hay logos inventados, imágenes generadas ni iconos ficticios. Falta el logo independiente y el PNG original azul con transparencia, según la decisión expresa del usuario. No se ha integrado ni comprobado el icono de ventana/barra de tareas.
4. **Permisos y ventanas pequeñas:** todos los accesos rápidos reflejan visible/managed/disabled de sus botones de navegación. Las pruebas comparan su disponibilidad con permisos reales de ADMINISTRATOR y WORKER; Clientes, Cartera e Inventario están autorizados para ambos roles actuales. Vendedores, Caja y Administración no aparecen para WORKER. Toda acción sigue revalidándose en los controladores/servicios del PR #24; ocultar controles no sustituye esa autorización. Logout deshabilita también los accesos rápidos. Cartera, Inventario y Login usan ScrollPane; los formularios pueden distribuirse en varias filas y las tablas mantienen una altura mínima legible.

Los indicadores globales, curva semanal y actividad unificada del prototipo siguen pendientes de fuentes públicas del controlador. No se introducen cifras ilustrativas como datos reales ni botones para funciones inexistentes.

## Recursos aprobados pendientes

Estructura preparada bajo `/com/laexclusiva/ui/images/` en el classpath:

- `brand/logo-original.svg` o `.png` y `brand/icons/la-exclusiva-{16,32,48,64,128,256}.png`; ICO `brand/icons/la-exclusiva.ico`.
- `mascot/mascot-blue-transparent.png`: botella original con rostro, corona, proporciones y logo intactos, delantal azul y transparencia real.

Los README de esas carpetas describen los archivos esperados; **todavía no existen esos gráficos** y los FXML no intentan cargarlos. La captura roja y las dos ediciones generativas descartadas no forman parte del producto. Cuando llegue el PNG aprobado se integrará sobre el degradado del banner sin rectángulo, marco o tarjeta opaca.

Carlos debe coordinar cualquier `stage.getIcons().addAll(...)` en App.java y la configuración del ICO del launcher Windows. Se necesitan los originales antes de preparar sus variantes, y una comprobación real de ventana/barra de tareas antes de declarar esa parte terminada.

## Verificación del 23/11/2026

Temurin 17.0.21+9, Maven 3.9.12, JavaFX 17.0.2. Comando local, con variables de destino PostgreSQL retiradas únicamente del proceso:

```text
mvn -o -Dlaexclusiva.test.postgres.users=false -Dlaexclusiva.test.postgres.userSecurityVersion=false -Dlaexclusiva.test.postgres.accountInstance=false clean verify
```

**828 casos: 769 aprobados, 59 integraciones PostgreSQL omitidas, 0 fallos y 0 errores; BUILD SUCCESS.** `git diff --check` también se revisa antes de publicar.

Nueve pruebas de VisualRefreshTest: carga de ocho vistas, navegación/admin real, distribución de Clientes, permisos por rol, rechazo de sesión sintética/revocada, logout y recuperación, paridad entre dashboard principal/alternativo, mensajes de login y contraste, desplazamiento de Cartera/Inventario. Son pruebas automatizadas sobre JavaFX real, no una revisión manual ni validación de PostgreSQL.

La primera suite completa detectó diez errores de montaje visual en pruebas antiguas que hacían `lookup()` antes de aplicar CSS a ScrollPane; otra repetición aisló tres casos de Cartera sin Scene. Se añadió Scene/CSS/layout a esos montajes, **sin quitar ni debilitar ninguna aserción de contraseñas, autorización o saldos**. No se modificó código de autenticación ni servicios comerciales.

Capturas reales mediante JavaFX `Node.snapshot`, con datos ficticios únicamente en fixtures memory: [login](ui-review/login.png), [error de login](ui-review/login-error.png), [dashboard](ui-review/dashboard.png), [dashboard compacto](ui-review/dashboard-compact.png), [Clientes compacto](ui-review/customers-compact.png), [Cartera compacta al final del desplazamiento](ui-review/portfolio-compact.png), [Inventario compacto al final del desplazamiento](ui-review/inventory-compact.png). No representan el marco ni la barra de tareas de Windows.

## Pendientes para revisión final

- Usuario/Carlos: logo original independiente y mascota original azul transparente; verificación visual definitiva con esos recursos.
- Carlos: integración Stage/launcher, fuentes de indicadores y recorrido manual coordinado de login, administración y logout.
- El PR permanece en Draft; no se hace merge a develop. No se ejecutan SQL, migraciones ni PostgreSQL en esta actividad.