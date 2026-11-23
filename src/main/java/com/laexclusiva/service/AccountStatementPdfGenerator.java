package com.laexclusiva.service;

import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePayment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Genera un PDF comercial con deuda de ventas y general separadas; no es factura electrónica DIAN. */
public final class AccountStatementPdfGenerator {
    private static final float LEFT = 48;
    private static final float TOP = 790;
    private static final float LEADING = 16;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** Produce bytes efímeros que pueden previsualizarse o adjuntarse a un mensaje consentido. */
    public byte[] generate(AccountStatementData data) {
        if (data == null) throw new IllegalArgumentException("Los datos del estado de cuenta son obligatorios.");
        List<String> lines = lines(data).stream().flatMap(AccountStatementPdfGenerator::wrap).toList();
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            int linesOnPage = 0;
            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                stream.beginText();
                stream.setLeading(LEADING);
                stream.newLineAtOffset(LEFT, TOP);
                for (String line : lines) {
                    if (linesOnPage == 47) {
                        stream.endText();
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        linesOnPage = 0;
                        stream = new PDPageContentStream(document, page);
                        stream.beginText();
                        stream.setLeading(LEADING);
                        stream.newLineAtOffset(LEFT, TOP);
                    }
                    if (line.startsWith("Estado de cuenta") && document.getNumberOfPages() == 1) stream.setFont(bold, 14);
                    else stream.setFont(regular, 10);
                    stream.showText(pdfText(line));
                    stream.newLine();
                    linesOnPage++;
                }
                stream.endText();
            } finally {
                stream.close();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo generar el estado de cuenta PDF.");
        }
    }

    private static List<String> lines(AccountStatementData data) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        List<String> result = new ArrayList<>();
        result.add("Estado de cuenta - " + data.businessName());
        result.add("Documento comercial informativo; no es factura electronica DIAN ni comprobante fiscal.");
        result.add("Cliente: " + data.customer().getName() + " (" + data.customer().getId() + ")");
        result.add("Generado: " + data.generatedDate());
        result.add("");
        result.add("VENTAS PENDIENTES");
        for (Sale sale : data.pendingSales()) {
            result.add("Venta " + sale.getId() + " | fecha " + sale.getCreatedAt().toLocalDate());
            result.add("  Total: " + money.format(sale.calculateTotal()) + " | saldo: " + money.format(sale.calculateBalance()));
            List<SalePayment> payments = sale.getPayments();
            for (int index = 0; index < payments.size(); index++) {
                SalePayment payment = payments.get(index);
                String kind = index == 0 && payment.getTimestamp().equals(sale.getCreatedAt())
                        ? "Pago inicial" : "Abono";
                result.add("  " + kind + " " + payment.getTimestamp().toLocalDate() + ": "
                        + money.format(payment.getAmount()));
            }
        }
        result.add("");
        result.add("Deuda de ventas: " + money.format(data.totalBalance().subtract(data.generalDebt()).max(BigDecimal.ZERO)));
        result.add("Deuda general (no asociada a ventas): " + money.format(data.generalDebt()));
        result.add("Saldo total actualizado: " + money.format(data.totalBalance()));
        result.add("Compromiso de pago: " + (data.commitmentDate() == null ? "No registrado" : data.commitmentDate()));
        result.add("Instrucciones: confirme con administracion el medio y titular antes de pagar.");
        if (data.paymentKey() != null) result.add("Llave de pago configurada: " + data.paymentKey());
        result.add("El pago requiere confirmacion administrativa; el correo no registra una deuda como pagada.");
        return result;
    }

    private static String pdfText(String value) {
        String safe = value.replace('\u2014', '-').replace('\u2013', '-').replace('\u2019', '\'');
        return safe.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    private static java.util.stream.Stream<String> wrap(String value) {
        if (value.isEmpty()) return java.util.stream.Stream.of(value);
        java.util.List<String> wrapped = new java.util.ArrayList<>();
        String remaining = value;
        while (remaining.length() > 88) {
            int cut = remaining.lastIndexOf(' ', 88);
            if (cut < 20) cut = 88;
            wrapped.add(remaining.substring(0, cut));
            remaining = "  " + remaining.substring(cut).stripLeading();
        }
        wrapped.add(remaining);
        return wrapped.stream();
    }
}
