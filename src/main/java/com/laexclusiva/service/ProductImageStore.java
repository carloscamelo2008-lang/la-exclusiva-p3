package com.laexclusiva.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Almacena fotografías locales por SHA-256 y admite exclusivamente PNG/JPEG pequeños y decodificables. */
public final class ProductImageStore {
    public static final String DIRECTORY_ENV = "LAEXCLUSIVA_PRODUCT_IMAGES_DIR";
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private final Path directory;

    /** Crea el almacén en una carpeta persistente local. */
    public ProductImageStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("La carpeta de fotografías es obligatoria.");
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Usa {@value #DIRECTORY_ENV} o una carpeta estable dentro del perfil del usuario. */
    public static ProductImageStore fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Construye la ruta desde una configuración inyectada, útil para pruebas. */
    public static ProductImageStore fromEnvironment(Map<String, String> environment) {
        if (environment == null) throw new IllegalArgumentException("El entorno de imágenes es obligatorio.");
        String configured = environment.get(DIRECTORY_ENV);
        Path path = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), "La Exclusiva", "Productos")
                : Path.of(configured.trim());
        return new ProductImageStore(path);
    }

    /**
     * Valida y copia una fotografía al almacén; el nombre derivado del hash no expone la ruta de origen.
     *
     * @param source archivo seleccionado por una persona administradora
     * @return nombre relativo estable para guardarlo junto al producto
     * @throws IOException si el archivo no es legible, soportado o seguro para decodificar
     */
    public String importImage(Path source) throws IOException {
        if (source == null || Files.isSymbolicLink(source) || !Files.isRegularFile(source)) {
            throw new IOException("Seleccione un archivo de imagen normal.");
        }
        long size = Files.size(source);
        if (size <= 0 || size > MAX_BYTES) throw new IOException("La imagen debe pesar entre 1 byte y 5 MB.");
        byte[] bytes = Files.readAllBytes(source);
        if (bytes.length != size || bytes.length > MAX_BYTES) {
            throw new IOException("El archivo cambió durante la lectura o supera 5 MB.");
        }
        String extension = validateImage(bytes);
        String filename = sha256(bytes) + "." + extension;
        prepareDirectory();
        Path destination = resolvePath(filename);
        try {
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException duplicateContent) {
            if (Files.isSymbolicLink(destination) || !java.util.Arrays.equals(Files.readAllBytes(destination), bytes)) {
                throw new IOException("La referencia de imagen existente no coincide con su contenido.");
            }
        }
        return filename;
    }

    /** Resuelve solo nombres SHA-256 admitidos; una imagen faltante queda como opcional y no bloquea ventas. */
    public Optional<Path> resolve(String filename) {
        if (filename == null) return Optional.empty();
        Path path = resolvePath(filename);
        return Files.isRegularFile(path) && !Files.isSymbolicLink(path) ? Optional.of(path) : Optional.empty();
    }

    /** Devuelve la carpeta configurada para respaldos locales. */
    public Path directory() {
        return directory;
    }

    private void prepareDirectory() throws IOException {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("La carpeta de fotografías no es válida.");
        }
    }

    private Path resolvePath(String filename) {
        if (!filename.matches("[0-9a-f]{64}\\.(png|jpg)")) {
            throw new IllegalArgumentException("La referencia de fotografía no es válida.");
        }
        Path path = directory.resolve(filename).normalize();
        if (!directory.equals(path.getParent())) throw new IllegalArgumentException("La ruta de fotografía no es válida.");
        return path;
    }

    private static String validateImage(byte[] bytes) throws IOException {
        String extension;
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
            extension = "png";
        } else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            extension = "jpg";
        } else {
            throw new IOException("Formato no admitido: use una fotografía PNG o JPEG.");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IOException("La fotografía no se puede decodificar.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("La imagen supera el tamaño de píxeles permitido.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IOException("La fotografía no se pudo leer.");
            } finally {
                reader.dispose();
            }
        }
        return extension;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossibleOnJava17) {
            throw new IOException("No está disponible SHA-256 para la fotografía.");
        }
    }
}
