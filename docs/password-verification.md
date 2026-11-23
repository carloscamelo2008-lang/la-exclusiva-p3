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

## Medición real acotada — 22/11/2026

Después del commit 4D se realizó la medición requerida, sin cambiar límites ni
parámetros de generación. Temurin 17.0.21+9 (Eclipse Adoptium), verificación pública
PasswordHasher.verify sobre hashes válidos, sal de 16 bytes y secreto sintético.
Para cada combinación: generar el fixture fuera del intervalo medido, una llamada
de calentamiento y tres muestras secuenciales con System.nanoTime(). No se
registraron secretos, hashes ni información de cuentas.

| Iteraciones | Resultado (bytes) | Mínimo ms | Mediana ms | Máximo ms |
|---|---:|---:|---:|---:|
| 210000 | 32 | 42.249 | 43.209 | 65.942 |
| 1000000 | 16 | 190.818 | 196.611 | 199.514 |
| 1000000 | 32 | 190.681 | 191.127 | 193.546 |
| 1000000 | 64 | 384.314 | 385.676 | 386.983 |

Las 16 verificaciones (cuatro calentamientos y doce muestras) fueron correctas.
La mediana del extremo medido fue aproximadamente nueve veces la del formato
actual. Esto satisface la medición acotada pendiente, pero no es un benchmark de
capacidad, un límite universal ni una garantía de latencia: JVM, carga, proveedor
y hardware influyen. Carlos y Jesús deben valorar concurrencia, límites de acceso
y ejecución fuera del hilo de interfaz durante la integración; no se modificaron
esas decisiones ni módulos en esta entrega. El inventario completo de hashes
históricos continúa pendiente y no se consultó ninguna base.

### Reproducción sin base de datos

Guardar el siguiente código en un archivo temporal IdentityPbkdf2Measurement.java
fuera de fuentes de producción, compilar el proyecto y ejecutar con el java de
Temurin 17 usando --class-path target/classes y la ruta del archivo. El programa
no se incorpora como prueba de rendimiento obligatoria ni impone umbrales frágiles.

```java
import com.laexclusiva.security.PasswordHasher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

// Medición local acotada, sin base de datos ni credenciales reales.
class IdentityPbkdf2Measurement {
    public static void main(String[] args) throws Exception {
        System.out.println("runtime=" + System.getProperty("java.runtime.version")
                + ";vendor=" + System.getProperty("java.vendor"));
        String secret = "Synthetic measurement fixture only";
        for (int[] item : new int[][]{{210000, 32}, {1000000, 16}, {1000000, 32}, {1000000, 64}}) {
            byte[] salt = new byte[16]; Arrays.fill(salt, (byte) 3);
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, item[0], item[1] * 8);
            byte[] result;
            try { result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
            finally { spec.clearPassword(); }
            String stored = "$pbkdf2-sha256$i=" + item[0] + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(result);
            if (!PasswordHasher.verify(secret, stored)) throw new AssertionError("Falló calentamiento");
            double[] milliseconds = new double[3];
            for (int sample = 0; sample < milliseconds.length; sample++) {
                long before = System.nanoTime();
                if (!PasswordHasher.verify(secret, stored)) throw new AssertionError("Falló verificación");
                milliseconds[sample] = (System.nanoTime() - before) / 1_000_000.0;
            }
            Arrays.sort(milliseconds);
            System.out.printf(Locale.ROOT, "iterations=%d bytes=%d samples=3 min_ms=%.3f median_ms=%.3f max_ms=%.3f%n",
                    item[0], item[1], milliseconds[0], milliseconds[1], milliseconds[2]);
            Arrays.fill(result, (byte) 0); Arrays.fill(salt, (byte) 0);
        }
    }
}

```
