package com.laexclusiva.ui.controller;

import com.laexclusiva.model.InsufficientStockException;

/** Filtra fallos internos antes de mostrar mensajes en las pantallas JavaFX. */
final class UiErrorMessages {
    private static final String INTERNAL_FAILURE =
            "No se pudo completar la operación. Inténtelo más tarde o consulte al administrador.";
    private static final java.util.Set<String> SAFE_AUTHORIZATION_MESSAGES = java.util.Set.of(
            "Solo un usuario con permiso de caja puede registrar movimientos.",
            "No tiene permiso para consultar caja.",
            "No tiene permiso para registrar ventas.",
            "No tiene permiso para consultar las ventas.",
            "No tiene permiso para consultar el catálogo.",
            "No tiene permiso para consultar inventario.",
            "No tiene permiso para consultar clientes.",
            "No tiene permiso para registrar clientes.",
            "No tiene permiso para consultar vendedores.",
            "No tiene permiso para registrar vendedores.",
            "No tiene permiso para consultar la cartera.",
            "No tiene permiso para registrar abonos.",
            "No tiene permiso para registrar deuda general.",
            "No tiene permiso para modificar el catálogo.",
            "No tiene permiso para consultar el inventario.",
            "No tiene permiso para registrar entradas de inventario.",
            "La venta debe registrarse con el vendedor asignado a la sesión.");

    private UiErrorMessages() { }

    /**
     * Conserva validaciones de entrada conocidas y oculta mensajes de infraestructura o errores inesperados.
     *
     * @param failure excepción capturada por el controlador
     * @return mensaje seguro para la interfaz, sin detalles JDBC ni trazas internas
     */
    static String safeMessage(Throwable failure) {
        if (failure instanceof InsufficientStockException) return knownMessage(failure);
        if (failure instanceof SecurityException) {
            String message = failure.getMessage();
            return SAFE_AUTHORIZATION_MESSAGES.contains(message)
                    ? message : "No tiene permiso para realizar esta operación.";
        }
        if (failure instanceof IllegalArgumentException) return knownMessage(failure);
        return INTERNAL_FAILURE;
    }

    private static String knownMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? INTERNAL_FAILURE : message;
    }
}
