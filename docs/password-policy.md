# Política centralizada de contraseñas — 4B

PasswordPolicy.validate valida secretos nuevos antes de PBKDF2: entre 15 y 128
puntos de código Unicode, sin sustitutos UTF-16 aislados. Permite frases, Unicode
y espacios iniciales/finales, sin exigir mayúsculas, números ni símbolos. No
recorta ni transforma el secreto para el hash. Un límite previo de 256 unidades
UTF-16 descarta entradas excesivas antes del conteo y de la normalización.

La comparación con la lista usa NFC y minúsculas Locale.ROOT y vuelve a NFC;
solo transforma una clave de comparación, nunca el secreto. La comparación es de
valor completo, sin filtros por subcadenas. Dos representaciones canónicamente
equivalentes pueden bloquearse igual aunque sigan siendo secretos distintos al
verificar un hash histórico. No se cambia la semántica de PasswordHasher.verify.

## Lista local y mantenimiento

El recurso UTF-8 password-blocklist.txt contiene un conjunto inicial ilustrativo
preparado con Codex para esta actividad: ejemplos comunes, secuencias y variantes
previsibles del proyecto. No se descargó ninguna lista ni se tomó un corpus de
filtraciones. No acredita detección exhaustiva de contraseñas comprometidas.
Jesús debe revisar procedencia, contenido y mantenimiento antes de aceptarlo como
lista definitiva. No se atribuye una aprobación de seguridad todavía pendiente.

Se carga una vez, de forma diferida, y se conserva como conjunto inmutable.
Un recurso ausente, vacío, ilegible, UTF-8 malformado o con entradas inválidas
impide crear/restablecer contraseñas; no se continúa sin bloqueo ni se imprime
el contenido. Los comentarios comienzan por #; no se admiten líneas vacías,
entradas de más de 256 unidades UTF-16 ni más de 10000 líneas. No deben añadirse
secretos reales. Cada actualización requiere revisión de Carlos/Jesús, procedencia
explicada y pruebas positivas/negativas. Ante fallo de carga, corregir el recurso
y reiniciar el proceso; nunca desactivar la política para sortear el error.

## Integración y compatibilidad

createUser y changePassword revalidan primero la sesión administrativa dentro de
la frontera y validan antes del hash y de la escritura. Se conserva el control de
versiones y no se renuevan sesiones. App y Main validan todas las contraseñas demo
antes de crear la primera cuenta; el administrador inicial también usa la política.
No se rediseña el bootstrap ni se convierte en persistente o concurrente.

Los datos ficticios cortos de las pruebas de aprovisionamiento se ampliaron;
no se establecieron credenciales predeterminadas de aplicación. Se reemplazaron
las expectativas de aceptar un solo espacio por rechazo sin escritura.

El login no exige la política nueva ni reescribe hashes: las contraseñas históricas
siguen verificándose. AuthenticationService, repositorios y PasswordHasher no se
modificaron. El futuro 4C tratará límites y diagnóstico de PasswordHasher.verify;
4B no soluciona sus parámetros no acotados ni su registro de excepciones.
El Login JavaFX rechaza solo contraseñas nulas o vacías y transmite el secreto
intacto, incluidos espacios iniciales/finales y 15 espacios. Conserva la validación
del username. JavaFX convierte setText(null) en texto vacío; la prueba verifica
ese comportamiento observable sin invocar autenticación.

## Evidencia y límites

364 pruebas dirigidas aprobadas con Temurin 17.0.21+9 y compilación Java 17,
sin fallos, errores ni omisiones: 24 de PasswordPolicy, 118 administrativas,
34 de autenticación/JavaFX y 188 regresiones adicionales de identidad/JDBC simulado.
La cobertura incluye recursos ausentes/ilegibles/malformados, Unicode, frases,
secretos originales, rechazo sin escritura y arranque demo sin cuentas parciales.
La consola se revisó estáticamente; no se afirma una ejecución interactiva ni una
suite general completa. No hubo conexiones PostgreSQL ni aplicación de V104/V105.
### Corrección posterior a la auditoría

El lector ya no utiliza readLine(): construye cada línea con un máximo de 256
unidades UTF-16 y rechaza la unidad adicional antes de incorporarla. El lector
bufferizado puede anticipar un bloque fijo de lectura; no acumula una línea
arbitrariamente grande. Conserva decodificación UTF-8 estricta, finales LF/CRLF/CR,
límite de 10000 líneas, cierre de recursos y conjunto inmutable.

La inicialización se prueba con clases aisladas para recurso ausente, vacío y
UTF-8 inválido. La primera y segunda llamada a validate() rechazan el secreto
con un error controlado, sin causas ni datos internos. No se recupera mediante
una lista vacía: debe corregirse el recurso y reiniciarse el proceso.

La ejecución posterior de corrección aprobó 376 pruebas con Temurin 17.0.21+9,
objetivo Java 17: 29 de política, 41 de autenticación/JavaFX, 118 administrativas
y 188 regresiones adicionales. Cero fallos, errores u omisiones. Las 12 pruebas
adicionales cubren Login, inicialización fallida y lectura acotada. El controlador
se ejercita cargando su FXML y pulsando el botón; el servicio observa el secreto
exacto y comprueba el hash real, sin continuar la navegación en esa prueba.
La consola sigue sin ejecución interactiva y no se ejecutó la suite general.
La revisión de Jesús sobre la lista ilustrativa continúa pendiente.