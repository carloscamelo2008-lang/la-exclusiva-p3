# Verificación PBKDF2 — Actividad 19, microcommit 4C

Carlos autorizó estos límites de lectura compatible; no son parámetros nuevos de
generación. Jesús no ha realizado todavía una revisión técnica de 4C. Su respuesta
favorable sobre 4B no se considera aprobación de este trabajo.

Formato exacto: `$pbkdf2-sha256$i=<decimal positivo>$<sal Base64>$<resultado Base64>`.
Se exigen cinco campos, primero vacío, identificador exacto y prefijo i= sin signos,
espacios, ceros iniciales ni caracteres adicionales. Se preservan campos vacíos
para rechazarlos explícitamente. El hash completo no puede exceder 255 caracteres.
Las iteraciones admitidas son 1000–1000000. La gramática limita a siete dígitos,
por lo que el entero se analiza sin riesgo de desbordamiento antes de validar rango.

La sal admite 8–64 bytes y el resultado 16–64 bytes. Se limita previamente el texto
Base64 (sal 11–88 caracteres y resultado 22–88) y luego se comprueba la longitud
decodificada. Se conserva el Base64 estándar válido sin padding; no se usa decoder
MIME ni se permiten caracteres inválidos. Ningún parámetro inválido llega a PBKDF2.
La contraseña y el hash no se recortan ni normalizan. No se aplica PasswordPolicy
al login ni se agrega un límite de contraseña que invalide secretos históricos.
Por ello el tamaño del String de contraseña sigue siendo un límite operativo
pendiente de acuerdo; 4C acota los parámetros provenientes del hash.

## Resultados y errores

Contraseña incorrecta o hash inválido: false, sin registros. NoSuchAlgorithmException,
InvalidKeySpecException y ProviderException: false con un único diagnóstico fijo,
sin parámetros, Throwable, mensajes de proveedor ni stacktrace. Los errores de
programación no se capturan genéricamente; tampoco Error, OutOfMemoryError o
ThreadDeath. El contrato externo de AuthenticationService permanece sin cambios.

La comparación usa MessageDigest.isEqual. Los arreglos temporales de verificación
se limpian en finally y PBEKeySpec.clearPassword elimina su copia interna. El helper
compartido también limpia el arreglo de caracteres usado al generar hashes, sin
cambiar algoritmo, 210000 iteraciones, sal de 16 bytes ni resultado de 32 bytes.
No se promete borrar el String original ni todas las copias internas del proveedor.
No hay rehash automático, escrituras de cuentas ni cambios de sesiones.

## Evidencia y límites

403 pruebas dirigidas aprobadas con Temurin 17.0.21+9 y compilación release 17,
sin fallos, errores u omisiones: PasswordHasherTest 27 y 376 regresiones de política,
autenticación/JavaFX, administración, usuarios, composición y JDBC simulado.
El fixture histórico real de 1000 iteraciones, sal de 8 bytes y resultado de 16
bytes verifica secretos exactos y Base64 con/sin padding. También se verifica el
hash actual.

No se dispone de un inventario completo de los hashes históricos del proyecto. La compatibilidad está demostrada para los hashes actuales y el fixture histórico conocido, pero no se garantiza para registros fuera de los límites aprobados.

La sobrecarga y la interfaz de derivación son internas al paquete, sin cambiar API
pública. Permiten comprobar límites y rechazo antes de derivar, y simular fallos
criptográficos sin modificar proveedores globales. El máximo de 1000000 se prueba
mediante esta simulación, no mediante un benchmark ni una derivación real de ese
coste. Se captura temporalmente el logger y se restaura su configuración en finally.
Se comprobaron mensajes fijos sin secretos ni excepciones adjuntas.

No se ejecutó la suite general ni PostgreSQL. V104/V105 siguen sin aplicar. No se
modificaron PasswordPolicy, lista, AuthenticationService, repositorios, JDBC o
navegación. La revisión de seguridad de Jesús sigue pendiente. No se inició 4D.

Antes del cierre definitivo de la integración de identidad debe realizarse una medición acotada del coste de verificación PBKDF2, especialmente con 1.000.000 de iteraciones y resultados de hasta 64 bytes. Esta evaluación sigue pendiente y no es requisito para el commit local de 4C.
## Refuerzo posterior de pruebas

La prueba de memoria ahora ejecuta dos rutas: éxito y excepción recuperable
InvalidKeySpecException. La contraseña y la sal observadas por la derivación
comienzan con valores distintos de cero y quedan borradas después de ambas rutas.
En éxito, el resultado calculado también comienza con bytes distintos de cero y
se comprueba borrado tras retornar. En el fallo no existe resultado devuelto que
pueda comprobarse. Los fixtures originales permanecen intactos.

El resultado esperado interno y PBEKeySpec no son accesibles mediante esta
inyección: su limpieza se confirma por inspección de los bloques finally de
verify y derive, no por observación directa de memoria. No se introdujo reflexión
invasiva, proveedor global ni cambio de producción para simular esa garantía.
Tampoco se promete borrar el String original o copias internas del proveedor.

El fixture histórico sin padding construye exclusivamente sus dos campos Base64
con Base64.getEncoder().withoutPadding(), conservando el prefijo i= intacto.

La nueva ejecución dirigida con Temurin 17.0.21+9 y objetivo Java 17 aprobó 404
casos: 28 de PasswordHasherTest y 376 regresiones, cero fallos, errores u omisiones.
La diferencia de un caso corresponde a parametrizar éxito/fallo de limpieza.
No hubo suite general, PostgreSQL, migraciones ni cambios adicionales de producción.
