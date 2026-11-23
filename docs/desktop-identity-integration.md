# Identidad de escritorio: propuesta de integración para Carlos

Estado del 23/11/2026: mascota original integrada; **logo independiente e iconos
todavía pendientes**. No se modifica App.java ni la configuración del launcher.

## Recursos requeridos

Esperar el logo original aprobado, sin extraerlo del delantal ni sustituirlo por
la mascota. Conservarlo en `com/laexclusiva/ui/images/brand/logo-original.svg`
o `.png`. Preparar PNG transparentes de 16, 32, 48, 64, 128 y 256 px y un ICO
multirresolución a partir de ese mismo recurso. Validar legibilidad y bordes
antes de usar sus variantes. No hay gráficos ficticios en las rutas reservadas.

## Cambio mínimo en JavaFX, sujeto a coordinación

En `App.start(Stage primaryStage)`, antes de `primaryStage.show()`, Carlos podrá
añadir las imágenes disponibles mediante `primaryStage.getIcons().addAll(...)`.
Se cargan con `App.class.getResource("/com/laexclusiva/ui/images/brand/icons/la-exclusiva-32.png")`
y las demás resoluciones, comprobando que cada URL exista antes de construir
su Image. No copiar esta referencia mientras los archivos sigan ausentes.

La aplicación actual reutiliza el Stage al cambiar entre login y dashboard:
los iconos pertenecen al Stage y no necesitan reinstalarse al cambiar la Scene.
Revisar también cualquier futura ventana independiente. Este ajuste no debe
alterar la composición, autenticación, autorización ni el cierre de sesión.

## Windows y empaquetado

Los iconos del Stage no demuestran por sí solos que el ejecutable, acceso
directo o agrupación de la barra de tareas estén configurados. Con un launcher
nativo, Carlos deberá incorporar el ICO a la configuración de jpackage:

```text
--icon src/main/resources/com/laexclusiva/ui/images/brand/icons/la-exclusiva.ico
--win-shortcut
--win-menu
```

Son opciones propuestas para el empaquetado existente/futuro, no un comando
completo de construcción ni una configuración aplicada. Se comprobó su
disponibilidad en `jpackage --help` del Temurin 17 instalado; no se ejecutó
jpackage para crear instaladores ni se instalaron herramientas.

## Aceptación pendiente

1. Recibir logo original y aprobar sus variantes/legibilidad a tamaño pequeño.
2. Acordar con Carlos el cambio mínimo de App.java y la configuración del launcher.
3. Construir y comprobar que el JAR/paquete incluye los recursos.
4. En Windows real, revisar ventana, barra de tareas, acceso directo y ejecutable,
   tanto en el primer inicio como después de login/logout y al volver a abrirlo.
5. Revisar instalación/desinstalación cuando exista un instalador acordado.

Hasta completar esas comprobaciones, la identidad de escritorio sigue pendiente.
