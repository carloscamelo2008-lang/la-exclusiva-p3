package com.laexclusiva.service;

import com.laexclusiva.model.StockRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Calcula una huella determinista de los campos que definen una solicitud de venta. */
public final class SaleRequestFingerprint {
    private SaleRequestFingerprint() { }

    /**
     * Produce una huella estable sin incluir precios actuales, que pueden cambiar después del primer intento.
     *
     * @param lines presentaciones, productos y cantidades solicitadas
     * @param sellerId vendedor
     * @param customerId cliente, o {@code null} para contado
     * @param initialPayment pago inicial
     * @return SHA-256 hexadecimal de la solicitud
     */
    public static String calculate(List<StockRequest> lines, String sellerId, String customerId,
                                   BigDecimal initialPayment) {
        if (lines == null || sellerId == null || initialPayment == null) {
            throw new IllegalArgumentException("Los datos de la solicitud de venta son obligatorios.");
        }
        StringBuilder canonical = new StringBuilder();
        canonical.append("seller=").append(sellerId).append('\n')
                .append("customer=").append(customerId == null ? "" : customerId).append('\n')
                .append("initial=").append(initialPayment.stripTrailingZeros().toPlainString()).append('\n');
        for (StockRequest line : lines) {
            if (line == null) throw new IllegalArgumentException("La solicitud contiene una línea nula.");
            canonical.append(line.productId()).append('|')
                    .append(line.presentation().code()).append('|')
                    .append(line.quantity()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("La plataforma no ofrece SHA-256.", impossible);
        }
    }
}
