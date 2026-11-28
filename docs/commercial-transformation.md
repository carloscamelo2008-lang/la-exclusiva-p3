# Transformación comercial: estado y decisiones pendientes

**Corte de trabajo:** 28 de noviembre de 2026. Esta rama local no se ha publicado ni fusionado.

## Cambios implementados en esta rama

- La composición de operación selecciona PostgreSQL por defecto y falla cerrada si faltan conexión, credenciales o esquema. El modo memoria exige selección explícita.
- La carga amplia de datos ficticios se conserva como fixture de pruebas. El modo comercial no la ejecuta. El argumento `--demo` se limita a cuentas temporales y requiere memoria explícita y contraseñas configuradas por el operador.
- Los clientes nuevos reciben un ID estable `CLI-<UUID>`. El formulario deja de pedir ID y cupo. V113 renombra el cupo histórico a `legacy_credit_limit` y conserva sus valores; las ventas nuevas no usan ese límite y el fiado permanece asociado a cada venta.
- La interfaz de recordatorios ya no presenta acciones de correo. La composición comercial rechaza `LAEXCLUSIVA_SMTP_ENABLED=true`; el seguimiento interno y el historial auditable permanecen.
- Los estados de cuenta PDF se guardan en una carpeta local configurable por `LAEXCLUSIVA_REPORTS_DIR`, con nombre único; la interfaz muestra la ruta y ofrece abrir la carpeta. El archivo es un informe de cartera, no facturación electrónica DIAN.
- El catálogo permite elegir una fotografía PNG/JPEG, valida tipo, decodificación, tamaño y dimensiones, la guarda por SHA-256 fuera de PostgreSQL y persiste solo una referencia. V114 añade la columna opcional con restricción SQL. La vista previa está disponible en inventario y ventas y muestra un estado alternativo cuando falta el archivo.
- El grupo de envase retornable permite escoger una opción existente o escribir una nueva.

La aplicación no carga ni importa datos reales del negocio. V113 y V114 se añadieron al repositorio, pero **no se aplicaron a ninguna base de datos en esta intervención**.

## Alcance aún pendiente antes de afirmar que el sistema es comercialmente completo

1. **Identificación del cliente:** Carlos debe definir tipo de documento, obligatoriedad, normalización, longitudes y regla de unicidad. No se inventó una política ni se agregó un campo con validación supuesta.
2. **Trabajador como responsable de venta:** la pantalla conserva el contrato actual de vendedor y el registro histórico `sale.seller_id`. La asignación de cuenta a vendedor funciona para trabajadores; falta una migración y contrato revisados que identifiquen al usuario autenticado en cada venta y permitan que el administrador venda con su propia identidad sin fabricar un vendedor ni alterar referencias históricas.
3. **Obligaciones de deuda general:** la cartera actual conserva el libro agregado con categoría y no permite seleccionar una obligación específica al abonar. Se requiere un modelo de obligación/asignación y una estrategia explícita para saldos históricos cuyos pagos no pueden atribuirse con certeza. La categoría todavía aparece en el flujo existente.
4. **Cuentas por pagar a proveedores:** compras y recepción distinguen pedido de mercancía recibida, pero no hay todavía libro de obligaciones, pagos parciales, vencimientos ni responsables del pago. No se presuponen plazos, tasas o acuerdos.
5. **Costos y ganancias:** no se presenta utilidad como resultado comercial hasta disponer de costos de compra confiables y su regla histórica por producto/lote. Ventas y efectivo son métricas distintas.
6. **Fotografías reales:** no se encontraron en `src/main/resources` las seis imágenes indicadas por Carlos; allí solo está la mascota aprobada. No se descargaron ni asignaron sustitutos. Para asociarlas, ubicar los archivos aprobados en una carpeta local y desde Catálogo seleccionar el producto por su ID estable y actualizar su foto. El almacén predeterminado es `%USERPROFILE%\La Exclusiva\Productos`; se puede cambiar con `LAEXCLUSIVA_PRODUCT_IMAGES_DIR`. Respaldar esa carpeta junto con PostgreSQL.
7. **Datos auténticos, configuración y despliegue:** faltan el catálogo validado, cantidades/precios, clientes, proveedores, usuarios/vendedores y la configuración de la base del negocio. No se conectó a una base de negocio ni se preparó un instalador. El procedimiento de respaldo/restauración se documentó en `database/README.md`, pero no se ejecutó ni ensayó.

## Validación de esta intervención

- Microsoft JDK 17.0.18 y Maven 3.10.0.
- La batería dirigida de almacén de imágenes, controladores JavaFX/FXML, plan de laboratorio y repositorio JDBC terminó `BUILD SUCCESS`: **33 contabilizadas, 30 aprobadas y 3 integraciones PostgreSQL omitidas**, con 0 fallos y 0 errores. Las 3 omisiones corresponden a `ProductRepositoryJdbcPostgresIntegrationTest`, porque no se configuró `LAEXCLUSIVA_TEST_DB_URL`. La ejecución final completa incluye además la regresión de vista previa en ventas.
- La última `mvn clean verify` con Microsoft JDK 17.0.18 y Maven 3.10.0 terminó `BUILD SUCCESS`: **918 contabilizadas, 851 aprobadas, 0 fallidas, 0 errores y 67 omitidas**. Las 67 corresponden a integraciones PostgreSQL optativas sin URL/propiedades de laboratorio. Se generó el JAR.
- La primera verificación completa detectó un test visual antiguo que buscaba el control `emailField`, retirado junto con la UI de correo. Se actualizó la lista del test a los controles internos actuales; `CollectionDashboardVisualTest` pasó 4/4 en la corrida final.
- Una verificación posterior llegó a generar los 918 reportes sin fallos, pero Surefire terminó con un error no reproducible al iniciar un fork; no hubo dump ni reporte de error individual. La repetición con `mvn -e clean verify` terminó correctamente y reportó los mismos totales.
- Docker no está instalado y no hay digest/secret del laboratorio configurados. No se ejecutó PostgreSQL real, no se aplicaron V113/V114 y no se modificó ninguna base local, compartida o productiva.
- `py -3` informó que no hay Python instalado; por eso tampoco se ejecutó el lanzador `actions-lab.py`. La secuencia y restricciones SQL sí tienen pruebas estáticas Java, pero esto no sustituye el laboratorio PostgreSQL.
- JavaFX/FXML se ejercitó mediante pruebas automatizadas, incluidas carga de imagen en inventario/ventas, nuevo grupo retornable y layouts de alerta/cartera; no se realizó un recorrido manual visual de escritorio en esta intervención.

## Configuración local

Para operación persistente, define `LAEXCLUSIVA_PERSISTENCE_MODE=postgres`, `LAEXCLUSIVA_DB_URL`, `LAEXCLUSIVA_DB_USER` y `LAEXCLUSIVA_DB_PASSWORD` en el proceso de la aplicación. Verifica y aplica manualmente las migraciones requeridas en una ventana controlada; el arranque no las ejecuta.

En una base vacía, el primer administrador usa `LAEXCLUSIVA_INITIAL_ADMIN_USERNAME` (por defecto `admin`) y `LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD`, que debe cumplir la política existente. El bootstrap no recrea ni reemplaza cuentas. Mantén la contraseña fuera del repositorio, la línea de comandos compartida y la bitácora.

Configura `LAEXCLUSIVA_PRODUCT_IMAGES_DIR` y `LAEXCLUSIVA_REPORTS_DIR` a carpetas incluidas en el procedimiento de respaldo del equipo. Si no se definen, se usan subcarpetas persistentes bajo el perfil del usuario.

## Revisión humana requerida

- Carlos: definir política de documento de identidad, entregar las seis fotos originales y datos comerciales validados, y revisar/ensayar el procedimiento de respaldo y restauración en una base aislada.
- Jesús: revisar el contrato/migraciones propuestos para identidad de actor de venta, obligaciones generales individualizadas y cuentas por pagar antes de habilitar esas escrituras.
- Daniel: conservar las funciones y `fx:id` agregados a Inventario y Ventas al continuar el diseño visual.
- Equipo: revisar V113/V114 y ejecutar sus pruebas en un laboratorio PostgreSQL efímero antes de aplicar las migraciones en una base de negocio.
