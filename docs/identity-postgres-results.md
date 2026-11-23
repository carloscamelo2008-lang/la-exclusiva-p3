# Evidencia real PostgreSQL — PR #22

Validación autorizada por Carlos y ejecutada exclusivamente en runners efímeros
de GitHub Actions. Fecha local: 22/11/2026 (logs UTC: 23/11/2026).

## Ejecuciones verificadas

- [PR combinado con develop, success](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45112354069).
- [Rama, success](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45112346376).
- Código ejecutado: ccb995afc2064be7e7af4d748de9f8319040c211. No se hizo merge.
- [Artefacto saneado del PR](https://github.com/carloscamelo2008-lang/la-exclusiva-p3/actions/runs/45112354069/artifacts/14789887647):
  39.051 bytes; SHA-256
  `28d020a1a11641191c895e94044b21ec2c16c448fe068e67b1dc67c086d12e03`.
  El hash del ZIP descargado coincide con el digest publicado por GitHub.
  Retención del artefacto: 14 días; este documento y su JSON conservan el resumen
  y checksums. El workflow permite repetir la validación, sin inventar un historial.

## Versiones e imágenes comprobadas

PostgreSQL **18.7 (Debian 18.7-1.pgdg12+5)**, Temurin
**17.0.20.1+1**, Maven **3.9.16**. La prueba local de regresión usó Temurin
17.0.21+9; no se confunden ambos runtimes ni se atribuye Java 25 a esta validación.

Imágenes oficiales resueltas contra Docker Registry y fijadas por manifiesto:

```text
postgres:18-bookworm@sha256:194e55feb2597b1465dbee81cc9fd8d204566cd2eb42c8d22fd6c931a38781b1
maven:3.9-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584
```

Se verificó SHA-256 de los manifiestos al resolverlos. Docker verificó las imágenes
al descargarlas por digest; sus inspecciones se incluyen en el artefacto.

## Aislamiento y ciclo de vida

Compose se inspeccionó antes de crear PostgreSQL; Docker antes de cualquier SQL
de prueba y antes de iniciar Java. Se comprobaron red interna, cero bindings de
puertos, ausencia de volúmenes Docker persistentes y datos en tmpfs. Repositorio,
secreto temporal y caché se montaron solo lectura; únicamente los informes Java
se exportaron a un directorio de evidencia. Java compartió el namespace de red
de PostgreSQL y conectó por 127.0.0.1:5432.

Base exacta la_exclusiva_test, usuario identity_validation_owner y cluster_name
laexclusiva-identity-validation se comprobaron antes de fixtures. No hubo secretos
del repositorio, cuentas reales ni conexiones a PostgreSQL de Windows, servicios
compartidos o producción. La caché se preparó antes del servidor, ejecutando solo
pruebas sin conexión; las integraciones corrieron offline y sin red externa.

Cada ciclo positivo y el escenario negativo usaron un servidor nuevo con datos
en memoria. finally y el paso always destruyeron los contenedores y redes del
proyecto único del job y eliminaron el secreto. summary.json confirma
`completed=true` y `destroyed=true`; la comprobación final no encontró contenedores
del laboratorio. Los datos de prueba dejaron de existir al destruirlo.

## Resultados por ejecución (rama y PR, cada una independientemente)

| Clase / fase | Primer ciclo | Repetición limpia |
|---|---:|---:|
| UserRepositoryJdbcPostgresIntegrationTest | 5 aprobadas | 5 aprobadas |
| UserSecurityVersionPostgresIntegrationTest | 11 aprobadas | 11 aprobadas |
| AccountInstancePostgresIntegrationTest | 3 aprobadas | 3 aprobadas |
| ActiveSellerConcurrencyPostgresIntegrationTest | 6 aprobadas | 6 aprobadas |
| Total Java | **25; 0 omitidas/fallos/errores** | **25; 0 omitidas/fallos/errores** |

Son 50 casos reales por ejecución, sin sumar ni confundir las dos ejecuciones
redundantes. Los ocho informes XML JUnit (cuatro por ciclo) se comprobaron en el artefacto.
Las seis combinaciones V105 son asignación/asignación, reactivación/reactivación
y asignación/reactivación, cada una con commit o rollback del primer escritor.
Se observaron dos PID distintos y contención real mediante pg_blocking_pids,
sin usar sleep para decidir el orden. Con commit, el segundo recibe 23505 del
índice correcto; con rollback, actualiza y revierte sin conservar cambios.
Las 11 pruebas V104 incluyen además sus dos contenciones de versión esperada.

## SQL, migraciones, rollback y recuperación

En ambos ciclos: V001 → dos cuentas históricas ficticias → V104 → V105 → V106.
Todas las aserciones SQL completaron sin error: versión inicial cero, UUID no
nulos/distintos, atributos anteriores intactos, rechazo de UUID sustituido/NULL,
INSERT que ignora UUID/versiones del cliente, reinserción con UUID distinto,
exclusividad de vendedor y restauración de atributos, UUID y versión tras ROLLBACK.

Los casos Java acreditaron sesiones rechazadas tras recrear el ID con ambos roles,
revocación, conflictos sin sobrescritura y conservación de hash histórico válido
al cambiar rol/estado, con UUID y fecha preservados. Los fixtures concurrentes
terminaron sus escritores antes de limpiar; la limpieza quedó limitada a nombres
exclusivos registrados. Los tres fixtures de identidad hicieron rollback.

Cada reaplicación se ejecutó en otro proceso psql y debía fallar: V001 y V105
con 42P07; V104 y V106 con 42701. Tras cada fallo se compararon catálogos y
UUID/versiones sin cambios. Estos errores esperados no son fallos JUnit ni
permiten afirmar idempotencia: la repetibilidad se demostró en un servidor nuevo.

En el tercer servidor, dos cuentas sintéticas activas compartieron vendedor antes
de V105: CREATE UNIQUE INDEX falló 23505 y revirtió su transacción. Se verificaron
V104 conservada, V105 ausente, V106 ausente y las dos filas intactas. Se desactivó
explícitamente solo el fixture iv_duplicate_b; después V105 y V106 se aplicaron.
Se comprobaron dos UUID distintos y versión máxima 1, más catálogos finales.
No fue una corrección automática de datos reales o de una base compartida.

Los SHA-256 de V001/V104/V105/V106 y resultados estructurados están en
[identity-postgres-results.json](identity-postgres-results.json). No cambiaron
las migraciones para lograr que las pruebas aprobaran.

## Correcciones de ejecución y límites

La primera definición fue rechazada antes de crear el laboratorio por usar el
contexto runner a nivel de env del job; la ruta se inicializa ahora en un paso.
El siguiente intento superó SQL y se detuvo por --no-deps no admitido en compose
create; se sustituyó por --no-recreate. Se destruyeron los laboratorios fallidos
y las ejecuciones anteriores enlazadas se repitieron íntegramente con éxito.
No se presentan esos intentos fallidos como ejecuciones completas.

No se validaron despliegues productivos, mínimos privilegios frente a propietarios
maliciosos ni integración comercial fuera del módulo. La autorización de aplicar
estas migraciones en bases compartidas o producción sigue siendo independiente.
La revisión y aprobación final del PR corresponden a Carlos y Jesús; no se realizó
merge ni se declara su aprobación a partir del resultado automatizado.
