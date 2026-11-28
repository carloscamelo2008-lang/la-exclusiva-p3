# Fotografías de catálogo — material de entrega

Esta carpeta contiene copias byte por byte de las seis fotografías sueltas entregadas por Carlos en `C:\Users\carlo\Downloads\imaenes la exclusiva`. Se decodificaron en Windows y se verificaron sus dimensiones y SHA-256. Los originales permanecen intactos. El ZIP que también había en la carpeta de origen no se copió; contenía los mismos seis nombres.

Estas copias son material de coordinación para que Daniel complete la integración visual. No pertenecen a `src/main/resources`, no se cargan al arrancar el programa y no se han importado al almacén persistente ni asociado a registros de PostgreSQL. Los hashes permiten verificar que no se editaron durante la copia.

| Archivo | Producto identificado en la foto | ID actual propuesto | Dimensiones | Bytes | SHA-256 |
|---|---|---|---:|---:|---|
| `poker_750ml.jpg` | Poker 750 ml | `POKER_750` | 800 × 800 | 80.876 | `2379fb603e93ceffdb8fcbef44dad0773e9088e65ede148a60a8e70576d93c54` |
| `poker_250ml.jpg` | Poker 250 ml | `POKER_250` | 600 × 960 | 52.424 | `12c7be5eef6c451c2d7436373bf184bffc588d3a99d8c381ed2e5660b5bde79e` |
| `aguila_250ml.jpg` | Águila Original 250 ml | `AGUILITA_250` | 800 × 800 | 60.987 | `78181fcf98cbf84bab936a11c52f8443414e6f2db281d5976f107a4991a7d684` |
| `cola_pola_330ml.jpg` | Cola & Pola 330 ml | `COLA_Y_POLA_LATA_330` | 554 × 554 | 45.933 | `895d9a426b1656721a5021b55e727240df7db6516eb6df911809a2db46c0143c` |
| `costena_bacana_330ml.jpeg` | Costeña Bacana 330 ml | `COSTENA_BACANA_330` | 1280 × 1280 | 93.991 | `5ca7424447dc1972df0d091450e89179c4800baecd12469a5a1781e26c80142b` |
| `costeñita_175ml.jpg` | Costeñita 175 ml | `COSTENITA_175` | 1920 × 3360 | 881.515 | `c6fafbd9424b1b478388ba8b67de2bcf9a38503525ef1df399b793d7f9f3895c` |

## Contrato de integración para la interfaz

- Confirmar primero el producto por su ID estable; no inferirlo por precio, cantidad ni posición en la tabla.
- El catálogo actual llama `Aguilita` al ID `AGUILITA_250`, mientras el archivo y la solicitud lo identifican como “Águila Original”. Carlos debe confirmar que se trata del mismo producto antes de guardar la referencia.
- El mecanismo de dominio existente (`ProductImageStore`) valida PNG/JPEG, límite de 5 MiB y 12 megapíxeles, importa el archivo al almacén local configurado y produce una referencia SHA-256. Las seis imágenes cumplen esos límites. Al importar un JPEG con extensión `.jpeg`, el almacén lo normaliza a una referencia `.jpg`; no cambia el original.
- La referencia se guarda por `ProductService.updateImageFilename` y el repositorio JDBC requiere V114. La carpeta persistente se configura con `LAEXCLUSIVA_PRODUCT_IMAGES_DIR` y debe respaldarse junto con PostgreSQL.
- Mantener las validaciones de permiso existentes y la vista alternativa cuando falte la imagen. Esta carpeta de documentación no debe leerse como ruta runtime ni copiarse en el instalador automáticamente.

No se incluyen precios, existencias ni cantidades porque no fueron suministrados como parte de estas fotografías.
