package com.laexclusiva;

import com.laexclusiva.ui.ConsoleMenu;

/**
 * Punto de entrada de la aplicación La Exclusiva.
 */
public final class Main {

    private Main() {
        // Evita instanciar la clase de inicio.
    }

    /**
     * Inicia el menú principal de la aplicación.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        new ConsoleMenu().start();
    }
}
