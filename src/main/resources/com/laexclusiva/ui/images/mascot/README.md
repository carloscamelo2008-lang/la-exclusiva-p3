# Mascota original aprobada

Recurso: `/com/laexclusiva/ui/images/mascot/mascot-blue-transparent.png`.
Fuente entregada por el usuario el 23/11/2026: `Botella Real La Exclusiva.png`.

- PNG de 931 × 1690 píxeles, 1.413.175 bytes.
- SHA-256: `cd1baeb2f29aa6631d5fb47622809adc9a38a8a13779bc59fda9aa4616d7a315`.
- Copia exacta del adjunto: sin redibujar, recortar, recolorear, reescalar el
  archivo, reconstruir mediante IA ni extraer su logotipo.
- Alfa real: 816.540 píxeles totalmente transparentes, 756.740 parcialmente
  transparentes y 110 opacos. Se conserva el alfa original, incluidas sombras
  y bordes suaves; no se convierte el fondo en un rectángulo opaco.
- Se inspeccionaron botella/corona y silueta sobre azul, blanco y negro.
  No se observaron residuos visibles que justificaran retocar el original;
  no se realizó limpieza destructiva ni modificación de píxeles.

Ambos dashboard FXML cargan el PNG desde el classpath con ImageView,
preserveRatio=true, sin viewport de recorte, sin marco ni panel opaco. FlowPane
mantiene separados texto e imagen y los dispone en filas al reducir el ancho.
La altura del banner crece y el dashboard permite desplazamiento vertical.

Las pruebas fijan la integridad del recurso aprobado, verifican alfa/carga,
proporción, ausencia de solapamiento y límites del banner a 1280/800/700 px.
Una futura sustitución requiere un nuevo original aprobado y actualizar
conjuntamente esta procedencia y la regresión de integridad.

Este archivo no sustituye al logo independiente ni al icono de Windows.
