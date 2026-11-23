# Identidad persistente de cada instancia de cuenta

Corrección del PR #22, autorizada por Carlos. V106 está preparada y **no aplicada**;
su backfill, triggers y concurrencia todavía requieren autorización y pruebas
PostgreSQL reales, además de la revisión independiente de Jesús.

## Contrato y sesiones

`account_instance_id` identifica una inserción, mientras `id` sigue siendo la
clave numérica y `security_version` identifica cambios de seguridad. El emisor
captura los tres valores y el validador exige que los tres coincidan con la
cuenta actual. Borrar y recrear una cuenta con el mismo ID, versión, username,
contraseña o rol no rehabilita una sesión anterior. La autoridad de emisión,
revocación y comprobación final concurrente conservan sus garantías anteriores.

Los constructores existentes de UserAccount y UserSession conservan sus firmas.
Una cuenta sin persistir puede tener UUID nulo; no permite emitir una sesión
auténtica. El setter del modelo sirve para mapear snapshots mutables, no para
cambiar la identidad persistida: memoria rechaza una actualización con UUID
distinto y JDBC incluye UUID en el WHERE junto a ID y versión. Las copias
defensivas y administrativas conservan el UUID. AuthenticatedPrincipal incorpora
el UUID como quinto componente inmutable, sin credenciales ni cuenta mutable.
Los consumidores existentes no construían este resultado directamente.

## V106 y escrituras externas

V106 agrega la columna UUID, asigna un UUID v4 a cada fila existente y establece
NOT NULL, DEFAULT y UNIQUE. Usa gen_random_uuid() integrado en PostgreSQL; no
instala extensiones. BEGIN/COMMIT mantiene el bloqueo DDL durante el backfill y
evita una ventana de inserciones sin identidad. No modifica campos de seguridad;
V104 conserva la versión cuando solo cambia esta columna.

El trigger BEFORE INSERT OR UPDATE siempre genera una identidad en INSERT,
ignorando cualquier UUID enviado por el cliente, y rechaza cambiarla en UPDATE.
Así, reutilizar un ID explícito tampoco reutiliza su identidad. Los UUID v4
ofrecen unicidad probabilística; UNIQUE rechaza una colisión entre filas
existentes sin sustituir datos. La generación aleatoria evita reutilización
deliberada incluso después de DELETE. No se promete resistencia frente a un
propietario o superusuario que altere el esquema o deshabilite triggers.

JDBC omite el UUID en ambos INSERT y recupera el valor confirmado con RETURNING.
UPDATE no lo incluye en SET. Cero filas produce conflicto sin reintentos. Un
snapshot de una cuenta eliminada tampoco puede actualizar la cuenta recreada.
Si una transacción exterior hace rollback, los metadatos del objeto Java pueden
representar un resultado no confirmado: debe descartarse y recargarse.

## Despliegue y validación pendientes

El adaptador requiere V104 y V106 antes de utilizarse. V105 sigue siendo necesaria
para la exclusividad de vendedor. Mantener las migraciones anteriores intactas;
la numeración V106 se comprobó en las referencias remotas disponibles. Coordinar
su reserva con Carlos antes de cualquier aplicación. No hay fallback al esquema
anterior ni ejecución automática de migraciones.

La aplicación requiere autorización específica, revisión de permisos y ventana
por el bloqueo de user_account. La migración no es idempotente: ante errores,
revisar la transacción y el esquema; no eliminar datos ni reejecutar a ciegas.
Pendiente probar en la_exclusiva_test: backfill, UUID distintos al reutilizar ID,
inmutabilidad incluso con SQL directo, INSERT con UUID impuesto, unicidad,
rollback, concurrencia y compatibilidad conjunta con V104/V105. Las pruebas
estáticas solo comprueban el texto preparado; los mocks no acreditan el trigger.

La integración comercial y navegación siguen a cargo de Carlos. Jesús debe
revisar el modelo de confianza, permisos de escritura y límites frente a DDL.
