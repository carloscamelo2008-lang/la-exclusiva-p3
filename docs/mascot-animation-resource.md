# Recurso pendiente para la animación de Exclusito

Estado: no implementada. El único recurso aprobado disponible es el PNG estático
original. No se deforma, recorta, sobrescribe ni reconstruye para simular un sorbo.
La alineación adaptable del banner funciona independientemente de la animación.

## Entrega gráfica necesaria

Se necesita una animación aprobada por el propietario de la identidad visual:

- Preferentemente una secuencia PNG RGBA con transparencia real, 24 fotogramas por
  segundo: entre 96 y 144 fotogramas para una acción de 4–6 segundos. Como referencia,
  120 fotogramas equivalen a 5 segundos.
- Todos los fotogramas con el mismo lienzo, escala, punto de apoyo y encuadre. El
  lienzo debe incluir la mano y la cerveza durante todo el gesto, sin recortes.
- Conservar exactamente corona, rostro, botella ámbar, guantes, zapatos, delantal
  azul, logo, proporciones, texturas y sombras del personaje aprobado.
- Secuencia: reposo → levantar cerveza → acercar a la boca → pequeño sorbo → bajar
  cerveza → reposo. Los extremos deben coincidir visualmente con la pose estática
  aprobada para evitar saltos al sustituirla.
- Sin fondo opaco, halo de color, destellos o movimientos ajenos a ese gesto.
- Incluir orden de fotogramas, duración y autorización de uso. Un GIF transparente
  también es viable, pero debe revisarse su paleta, bordes y eliminación de cada
  fotograma para evitar estelas; PNG conserva mejor el alfa y los degradados.

No basta un GIF de otra mascota o una transformación del PNG que mueva toda la
imagen. No se ha generado ni aprobado una animación nueva en esta tarea.

## Contrato previsto para JavaFX 17

Una vez recibido y revisado el recurso, un componente exclusivamente visual podrá
reproducir los fotogramas mediante Timeline sobre el mismo ImageView, respetando
su tamaño proporcional y margen derecho. No se integra ese componente sin recurso.

- Un ciclo de referencia: 5 segundos de gesto y 20 de reposo (25 segundos entre
  comienzos); nunca animación continua ni temporizadores fuera de la pantalla.
- Opción visible «Reducir movimiento»: mantener el PNG original, detener el ciclo
  y volver al reposo. Debe poder desactivarse completamente sin afectar navegación.
- Usar la imagen estática también si faltan fotogramas o falla su carga. El PNG
  original sigue siendo la alternativa disponible y no se sobrescribe.
- Parar y liberar el ciclo al salir del dashboard o cerrar sesión. Coordinar ese
  ciclo de vida con Carlos si requiere tocar MainController; no modificar App.java.
- Comprobar memoria, fluidez, pausas, reducción de movimiento y cierre/reapertura
  con el recurso real antes de declarar terminada la animación.

Mientras falte esta entrega, el dashboard permanece totalmente estático: no hay
un botón que aparente habilitar una animación inexistente.
