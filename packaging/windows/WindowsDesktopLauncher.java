package com.laexclusiva.packaging;

import com.laexclusiva.ui.App;
import java.util.Arrays;

/**
 * Entrada gráfica para la distribución de escritorio de Windows.
 *
 * <p>La distribución se inicia siempre en el flujo normal. El cargador de datos
 * de demostración pertenece al arranque de desarrollo y no se habilita desde
 * el ejecutable distribuido.</p>
 */
public final class WindowsDesktopLauncher {
    private WindowsDesktopLauncher() {
    }

    /**
     * Inicia JavaFX y evita exponer el modo de carga de datos de demostración.
     *
     * @param args argumentos recibidos desde el launcher de Windows
     * @throws IllegalArgumentException si se intenta iniciar el modo de demostración
     */
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--demo")) {
            throw new IllegalArgumentException(
                    "El modo de demostración no está disponible en la distribución de escritorio.");
        }
        App.launch(App.class, args);
    }
}
