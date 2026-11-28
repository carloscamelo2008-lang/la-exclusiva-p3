package com.laexclusiva.service;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** Guarda estados de cuenta en una carpeta local, configurable y sin sobrescribir reportes previos. */
public final class AccountStatementReportStore {
    public static final String DIRECTORY_ENV = "LAEXCLUSIVA_REPORTS_DIR";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path directory;

    /**
     * Crea un almacén de reportes en la carpeta indicada.
     *
     * @param directory carpeta local destino
     * @throws IllegalArgumentException si la ruta es nula
     */
    public AccountStatementReportStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("La carpeta de reportes es obligatoria.");
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Usa {@value #DIRECTORY_ENV} o una carpeta portable bajo el perfil del usuario. */
    public static AccountStatementReportStore fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Selecciona la ruta de reportes desde un mapa de configuración comprobable en pruebas. */
    public static AccountStatementReportStore fromEnvironment(Map<String, String> environment) {
        if (environment == null) throw new IllegalArgumentException("El entorno de reportes es obligatorio.");
        String configured = environment.get(DIRECTORY_ENV);
        Path path = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), "La Exclusiva", "Reportes")
                : Path.of(configured.trim());
        return new AccountStatementReportStore(path);
    }

    /**
     * Persiste los bytes de un PDF con nombre único y movimiento sin reemplazo.
     * El temporal se crea en la misma carpeta para que el cierre de archivo ocurra antes de publicarlo.
     *
     * @param customerId identificador del cliente, usado solo como parte sanitizada del nombre
     * @param generatedDate fecha del estado de cuenta
     * @param pdf contenido PDF generado por el servicio
     * @return ruta absoluta del reporte guardado
     * @throws IllegalArgumentException si faltan datos o el contenido no parece un PDF
     * @throws IOException si la carpeta o el archivo no pueden crearse
     */
    public Path save(String customerId, LocalDate generatedDate, byte[] pdf) throws IOException {
        if (customerId == null || customerId.isBlank() || generatedDate == null || !isPdf(pdf)) {
            throw new IllegalArgumentException("El reporte requiere cliente, fecha y un PDF válido.");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("La ruta de reportes no es una carpeta local válida.");
        }
        String customerSlug = customerId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (customerSlug.length() > 40) customerSlug = customerSlug.substring(0, 40);
        String timestamp = LocalDateTime.now(ZoneId.of("America/Bogota")).format(STAMP);
        Path temporary = Files.createTempFile(directory, ".estado-cuenta-", ".tmp");
        try {
            Files.write(temporary, pdf, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Path destination = directory.resolve("estado-cuenta-" + customerSlug + "-" + generatedDate
                    + "-" + timestamp + "-" + UUID.randomUUID() + ".pdf");
            return Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Abre la carpeta local de reportes con el explorador de archivos predeterminado. */
    public void openDirectory() throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("La ruta de reportes no es una carpeta local válida.");
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Este entorno no permite abrir carpetas desde la aplicación.");
        }
        Desktop.getDesktop().open(directory.toFile());
    }

    /** Devuelve la carpeta configurada, aún si no se ha creado en el disco. */
    public Path directory() {
        return directory;
    }

    private static boolean isPdf(byte[] pdf) {
        return pdf != null && pdf.length >= 5 && pdf[0] == '%' && pdf[1] == 'P'
                && pdf[2] == 'D' && pdf[3] == 'F' && pdf[4] == '-';
    }
}
