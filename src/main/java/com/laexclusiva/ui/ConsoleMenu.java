package com.laexclusiva.ui;

import java.util.Scanner;

/**
 * Presenta el menú de consola y recibe las opciones del usuario.
 * Las operaciones se incorporarán en sus respectivas ramas de funcionalidad.
 */
public final class ConsoleMenu {

    /**
     * Inicia el ciclo principal del menú.
     */
    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
                showMenu();
                String option = scanner.nextLine().trim();

                switch (option) {
                    case "1" -> showPendingModule("Gestión de productos e inventario");
                    case "2" -> showPendingModule("Gestión de ventas");
                    case "3" -> showPendingModule("Clientes, fiados y abonos");
                    case "4" -> showPendingModule("Ingresos y egresos");
                    case "5" -> showPendingModule("Consultas y reportes");
                    case "0" -> {
                        System.out.println("Gracias por utilizar La Exclusiva.");
                        running = false;
                    }
                    default -> System.out.println("Opción no válida. Intente nuevamente.");
                }
            }
        }
    }

    private void showMenu() {
        System.out.println();
        System.out.println("======================================");
        System.out.println("              LA EXCLUSIVA");
        System.out.println("       Sistema de administración");
        System.out.println("======================================");
        System.out.println("1. Productos e inventario");
        System.out.println("2. Ventas");
        System.out.println("3. Clientes, fiados y abonos");
        System.out.println("4. Ingresos y egresos");
        System.out.println("5. Consultas y reportes");
        System.out.println("0. Salir");
        System.out.print("Seleccione una opción: ");
    }

    private void showPendingModule(String moduleName) {
        System.out.println(moduleName + ": funcionalidad pendiente de implementación.");
    }
}
