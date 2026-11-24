# Empaquetado de escritorio para Windows

Esta guía construye una distribución Windows x64 de **La Exclusiva** con Java 17,
JavaFX y sus bibliotecas nativas de Windows. `jpackage` incorpora una imagen de
runtime Java, por lo que el equipo de destino no necesita instalar Java, Maven
ni un IDE. El proceso produce primero un `app-image` y, cuando se solicita, un
instalador MSI con acceso directo de escritorio y entrada en el menú Inicio.

## Requisitos de construcción

- Windows 10/11 x64.
- JDK 17 x64 con `javac`, `jar` y `jpackage`; Java 17 se fija en el script.
- Maven 3.8 o posterior.
- Conexión a Maven Central para dependencias todavía no almacenadas en caché.
- Para MSI, WiX Toolset 3.14.1. El script acepta `-WixBin`; si no se indica,
  descarga el ZIP portable oficial fuera del repositorio, fija la versión y
  comprueba su SHA-256 antes de extraerlo. WiX se guarda en `%LOCALAPPDATA%` y
  no se instala globalmente ni se incluye en el producto.

`jpackage` crea paquetes nativos en el sistema operativo de destino; el MSI se
debe generar en Windows. JavaFX se resuelve con clasificadores `win` mediante
Maven, por lo que el producto no reutiliza bibliotecas nativas de Linux o macOS.

## Construir

Desde PowerShell en la raíz del repositorio:

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-17'
.\scripts\package-windows.ps1 -Target app-image
.\scripts\package-windows.ps1 -Target all
```

Si Maven no está en `PATH`:

```powershell
.\scripts\package-windows.ps1 -Target all `
  -Maven 'C:\ruta\a\apache-maven\bin\mvn.cmd' `
  -JavaHome $env:JAVA_HOME
```

Los resultados quedan bajo `dist\windows-package\<id-de-build>\`. Cada
ejecución usa una carpeta nueva y no sobrescribe distribuciones anteriores.
`validation-summary.txt` registra versiones, recuento de Surefire y rutas de
artefactos, nunca valores de variables de entorno.

El script ejecuta `mvn clean verify`, copia solo dependencias del alcance
`runtime`, comprueba las bibliotecas PostgreSQL JDBC, PDFBox, Angus Mail y las
cuatro bibliotecas JavaFX con clasificador `win`, y compila una entrada gráfica
dedicada. El JAR de distribución excluye `Main` y `DemoDataInitializer`; el
launcher rechaza `--demo`. El modo normal sigue siendo `memory` y los datos se
pierden al cerrar. Las pruebas PostgreSQL no se conectan: el script retira las
variables JDBC y SMTP del proceso durante la verificación y el smoke launch.

El resultado `app-image` incluye `La Exclusiva.exe` y la imagen recortada de
runtime Java (`runtime\lib\modules` y la JVM nativa). El smoke launch inicia JavaFX con una cuenta ficticia
creada solo en la memoria del proceso y la cierra después de comprobar que
permanece activa. Esto valida el arranque, no sustituye un recorrido manual de
las pantallas ni comprueba persistencia PostgreSQL.

## Marca

El logo original aprobado y el icono ICO siguen pendientes. No se reconstruye
el logo ni se modifica la mascota. La ruta reservada para el ICO oficial es:

```text
src/main/resources/com/laexclusiva/ui/images/brand/icons/la-exclusiva.ico
```

También se puede pasar un archivo aprobado con `-IconPath`. Sin ese recurso,
`jpackage` genera un artefacto de prueba con icono genérico. Esa variante no es
la identidad visual final. La mascota se empaqueta sin transformación desde el
recurso original documentado en `src/main/resources/com/laexclusiva/ui/images/mascot/README.md`.

## Configuración segura de operación

La distribución no contiene usuarios, contraseñas, URL de bases, tokens ni
secretos SMTP. No se carga catálogo o información comercial de demostración y
no se aplica ninguna migración al iniciar.

### Demostración sin persistencia

El modo predeterminado es `memory`. En el primer inicio, si no hay cuenta, la
aplicación solicita mediante el entorno del proceso:

```text
LAEXCLUSIVA_INITIAL_ADMIN_USERNAME   (opcional; predeterminado: admin)
LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD   (obligatoria si el repositorio está vacío)
```

Use una contraseña individual que cumpla la política de la aplicación y
configure ambas variables solo para el proceso del primer inicio. Al cerrar la
aplicación se pierde toda la información creada en memoria. No reutilice esta
modalidad para operar el negocio.

### Operación persistente con PostgreSQL

Configure explícitamente, en el entorno protegido del usuario que ejecuta la
aplicación:

```text
LAEXCLUSIVA_PERSISTENCE_MODE=postgres
LAEXCLUSIVA_DB_URL=jdbc:postgresql://servidor:5432/base
LAEXCLUSIVA_DB_USER=<usuario de aplicación>
LAEXCLUSIVA_DB_PASSWORD=<secreto administrado fuera del repositorio>
```

Prepare previamente el esquema siguiendo `database/README.md` y el contrato de
integración JDBC. El arranque no ejecuta migraciones ni cambia a memoria ante
errores de conexión, permisos o esquema incompleto. Configure el primer
administrador con `LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` y
`LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD` solo si la base persistente aún no tiene
usuarios; después, elimine esas variables del entorno del proceso. Proteja las
credenciales con el administrador de secretos del sistema y no las escriba en
scripts, accesos directos compartidos, archivos del proyecto o logs.

El envío SMTP real permanece desactivado. Esta guía no habilita ni configura
SMTP. No conecte la verificación de empaquetado a bases locales, compartidas o
productivas.

## Instalación, comprobación y desinstalación

El instalador es MSI por usuario e incorpora accesos directos al escritorio y
al menú Inicio. La versión de prueba sin firma puede producir advertencias de
Windows SmartScreen; el instalador no está firmado ni es una versión de
distribución final. El desinstalador elimina el programa instalado. No se
ejecuta código de limpieza de bases ni se borran directorios de datos del
usuario; la aplicación no guarda persistencia local en modo memoria. La
operación PostgreSQL conserva sus datos en el servidor y debe administrarse por
separado.

Lista manual para validar en Windows antes de entregar a usuarios:

1. Instalar el MSI por usuario y abrir «La Exclusiva» desde Inicio y el acceso
   directo del escritorio.
2. Iniciar sesión con cuentas ficticias de administrador y trabajador; revisar
   dashboard, Inventario, Ventas, Compras/Recepción, Cartera, Alertas y PDF.
3. Cerrar sesión, cerrar la aplicación y volver a abrirla. Confirmar la pérdida
   de datos solo en modo memoria y la persistencia solo en una PostgreSQL de
   pruebas configurada de forma explícita.
4. Probar PostgreSQL no configurado o inválido y confirmar que el arranque falla
   cerrado, sin sesión alternativa en memoria.
5. Desinstalar desde Configuración de Windows y confirmar que no se eliminan
   datos PostgreSQL ni archivos del usuario.
6. Repetir la inspección con el logo e ICO oficiales cuando sean aprobados.

Estas pruebas manuales no se consideran aprobadas hasta ejecutarlas en cada
equipo de destino. El smoke launch y `mvn clean verify` son validaciones
automatizadas separadas.

## Evidencia ejecutada el 23 de noviembre de 2026

En el equipo Windows que reportó `Windows 10 Home Single Language`, build
26200, se construyó con Microsoft JDK 17.0.18, Maven 3.10.0 y WiX Toolset
3.14.1. El script verificó el SHA-256 del ZIP portable de WiX antes de
extraerlo. La ejecución final terminó correctamente y creó:

- `dist/windows-package/20261123-232207-cbe28453/app-image/La Exclusiva/`:
  imagen de aplicación con 371 archivos (151.681.491 bytes), launcher
  `La Exclusiva.exe` y runtime Java incluido.
- `dist/windows-package/20261123-232207-cbe28453/installer/La Exclusiva-1.0.0.msi`:
  65.066.402 bytes, SHA-256
  `A9899F091AEB947F39B1A0DB5E14D0AA99CD208378418C0177435684E4904069`.

La inspección de la imagen confirmó el runtime Java, los JAR con clasificadores
JavaFX `win`, PostgreSQL JDBC y PDFBox. No se incluyeron `Main` ni
`DemoDataInitializer` en el JAR de escritorio. `mvn clean verify` contabilizó
879 pruebas: 817 aprobadas, 0 fallidas, 0 errores y 62 omitidas, todas las
integraciones PostgreSQL optativas al retirar deliberadamente las variables de
conexión. El smoke launch mantuvo JavaFX activo durante 12 segundos. No se
conectó a PostgreSQL ni SMTP durante la construcción.

También se instaló el MSI por usuario, se abrió la aplicación desde el acceso
directo del menú Inicio, y se comprobaron el login de una cuenta administradora
ficticia, el dashboard y la navegación visible de Inventario, Ventas,
Compras/Recepción, Cartera, Alertas y Caja. Se creó en memoria un vendedor y una
cuenta trabajadora ficticios; el login de trabajador funcionó, su dashboard
mostró ese rol, y los accesos de administración/caja no aparecieron. Se
comprobó que los controles de escritura de Compras estaban deshabilitados y que
el vendedor asignado del trabajador no era editable. Tras cerrar sesión,
cerrar y volver a abrir la aplicación, se pudo iniciar sesión otra vez con el
administrador temporal porque el bootstrap de memoria se ejecutó de nuevo; los
registros de memoria no constituyen persistencia.

Con el modo PostgreSQL seleccionado y sin variables de conexión, el launcher
no mostró un login alternativo en memoria y presentó el diálogo genérico
`Failed to launch JVM`. Esto es evidencia de que no se observó una sesión de
respaldo, pero el mensaje no identifica la causa interna y debe mejorarse o
diagnosticarse antes de una entrega operativa. La desinstalación MSI terminó
con código 0, quitó la entrada de programa y los accesos directos del menú
Inicio/escritorio; no se había creado almacenamiento comercial local y no se
tocó base de datos alguna.

No se completó una transacción funcional desde JavaFX: alta/entrada de
producto, venta, abono, compras/recepción, generación/apertura de PDF ni una
sesión conectada a PostgreSQL siguen pendientes de prueba manual. Las pruebas
visuales descritas no equivalen a esas comprobaciones ni a validación sobre
Windows 10/11 en otros equipos.

## Limitaciones de esta versión de empaquetado

- Falta el logo independiente y el ICO oficial.
- El MSI de prueba no tiene firma digital; falta certificado y proceso de firma.
- No se realiza recorrido completo de cada módulo desde el instalador por el
  script automatizado.
- Las 62 integraciones PostgreSQL fueron omitidas en esta ejecución local; no
  se atribuyen como aprobadas. La persistencia desde la interfaz no se probó.
- Cuando se selecciona PostgreSQL sin configuración, el launcher observó el
  error genérico `Failed to launch JVM`; aunque no hubo fallback visible a
  memoria, aún hace falta mejorar la información segura del fallo.
- El empaquetado y las pruebas automáticas no validan la instalación en todos
  los builds de Windows 10/11 ni la política antivirus de cada equipo.
- SMTP real y migraciones automáticas siguen deshabilitados.
