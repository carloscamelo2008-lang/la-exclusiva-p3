package com.laexclusiva.service;

import com.laexclusiva.model.Sale;
import com.laexclusiva.model.SalePayment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
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
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - LEFT * 2;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** Produce bytes efímeros que pueden previsualizarse o adjuntarse a un mensaje consentido. */
    public byte[] generate(AccountStatementData data) {
        if (data == null) throw new IllegalArgumentException("Los datos del estado de cuenta son obligatorios.");
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<String> lines = wrapLines(lines(data));
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
                    if (line.startsWith("Estado de cuenta") && document.getNumberOfPages() == 1) {
                        stream.setFont(bold, 14);
                        stream.setNonStrokingColor(new Color(31, 78, 121));
                    } else {
                        stream.setFont(regular, 10);
                        stream.setNonStrokingColor(Color.BLACK);
                    }
                    stream.showText(line);
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
        result.add("Los pagos incluidos constan como registrados hasta la fecha de generación; verifique cualquier pago posterior en el sistema.");
        return result;
    }

    private List<String> wrapLines(List<String> source) throws IOException {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            boolean title = line.startsWith("Estado de cuenta");
            result.addAll(wrap(pdfText(line), title ? bold : regular, title ? 14 : 10));
        }
        return result;
    }

    private static String pdfText(String value) {
        String safe = value.replace('\u2014', '-').replace('\u2013', '-').replace('\u2019', '\'');
        return safe.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    private static List<String> wrap(String value, PDType1Font font, float fontSize) throws IOException {
        if (value.isEmpty()) return List.of(value);
        List<String> wrapped = new ArrayList<>();
        String remaining = value;
        while (!remaining.isEmpty()) {
            float width = 0;
            int cut = 0;
            int lastSpace = 0;
            for (int offset = 0; offset < remaining.length();) {
                int codePoint = remaining.codePointAt(offset);
                int next = offset + Character.charCount(codePoint);
                String glyph = new String(Character.toChars(codePoint));
                float glyphWidth = font.getStringWidth(glyph) * fontSize / 1000f;
                if (width + glyphWidth > CONTENT_WIDTH) break;
                width += glyphWidth;
                cut = next;
                if (Character.isWhitespace(codePoint) && !remaining.substring(0, next).isBlank()) {
                    lastSpace = next;
                }
                offset = next;
            }
            if (cut == remaining.length()) {
                wrapped.add(remaining);
                break;
            }
            if (cut == 0) cut = Character.charCount(remaining.codePointAt(0));
            int breakAt = lastSpace > 0 ? lastSpace : cut;
            String segment = remaining.substring(0, breakAt).stripTrailing();
            if (segment.isEmpty()) {
                breakAt = cut;
                segment = remaining.substring(0, breakAt).stripTrailing();
            }
            wrapped.add(segment);
            remaining = "  " + remaining.substring(breakAt).stripLeading();
        }
        return wrapped;
    }
}
