package com.laexclusiva.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regresiones del almacén de fotografías: formato, persistencia y referencias sin rutas arbitrarias. */
class ProductImageStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsValidatedImageByStableContentHashAndResolvesItAfterReconstruction() throws Exception {
        Path source = temporaryDirectory.resolve("poker.png");
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        Path storePath = temporaryDirectory.resolve("portable-products");

        String reference = new ProductImageStore(storePath).importImage(source);
        ProductImageStore reopened = new ProductImageStore(storePath);

        assertTrue(reference.matches("[0-9a-f]{64}\\.png"));
        assertEquals(reference, reopened.importImage(source));
        assertTrue(reopened.resolve(reference).isPresent());
        assertTrue(Files.isRegularFile(reopened.resolve(reference).orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> reopened.resolve("../poker.png"));
    }

    @Test
    void rejectsUnsupportedOrCorruptImageAndDoesNotCreateAStoredFile() throws Exception {
        Path source = temporaryDirectory.resolve("fake.png");
        Files.writeString(source, "not an image");
        Path storePath = temporaryDirectory.resolve("products");
        ProductImageStore store = new ProductImageStore(storePath);

        assertThrows(IOException.class, () -> store.importImage(source));
        assertFalse(Files.exists(storePath));
    }
}
