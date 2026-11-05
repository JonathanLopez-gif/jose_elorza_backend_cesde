package com.joselorza.app.infrastructure.adapter.pdf;

import com.joselorza.app.application.port.out.ReceiptData;
import com.joselorza.app.application.port.out.ReceiptGeneratorPort;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el comprobante de pago en PDF con OpenPDF.
 * Incluye datos del usuario, servicio, transacción e IVA desglosado (base + IVA = total).
 */
@Component
public class OpenPdfReceiptGenerator implements ReceiptGeneratorPort {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm (z)");
    private static final Color HEADER_COLOR = new Color(33, 37, 41);
    private static final Color LIGHT_GRAY = new Color(240, 240, 240);

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, HEADER_COLOR);
    private final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, HEADER_COLOR);
    private final Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private final Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private final Font smallFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

    @Override
    public byte[] generate(ReceiptData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // --- Encabezado ---
            Paragraph title = new Paragraph("Comprobante de pago", titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);
            document.add(new Paragraph("No. " + data.receiptNumber(), valueFont));
            document.add(spacer());

            // --- Emisor ---
            document.add(new Paragraph("Emisor", sectionFont));
            PdfPTable issuer = twoColumnTable();
            addRow(issuer, "Nombre", data.issuerName());
            if (StringUtils.hasText(data.issuerId())) {
                addRow(issuer, "NIT / Documento", data.issuerId());
            }
            addRow(issuer, "Correo", data.issuerEmail());
            document.add(issuer);
            document.add(spacer());

            // --- Cliente ---
            document.add(new Paragraph("Cliente", sectionFont));
            PdfPTable customer = twoColumnTable();
            addRow(customer, "Nombre", data.customerName());
            addRow(customer, "Correo", data.customerEmail());
            if (StringUtils.hasText(data.customerPhone())) {
                addRow(customer, "Teléfono", data.customerPhone());
            }
            document.add(customer);
            document.add(spacer());

            // --- Transacción ---
            document.add(new Paragraph("Detalle de la transacción", sectionFont));
            PdfPTable transaction = twoColumnTable();
            addRow(transaction, "Servicio / Plan", data.serviceName());
            addRow(transaction, "Concepto", data.concept());
            if (data.appointmentAt() != null) {
                addRow(transaction, "Fecha de la sesión", format(data.appointmentAt()));
            }
            addRow(transaction, "Pasarela", data.gateway());
            addRow(transaction, "No. de transacción", data.transactionNumber());
            addRow(transaction, "Fecha de pago", format(data.paidAt()));
            addRow(transaction, "Estado", data.status());
            document.add(transaction);
            document.add(spacer());

            // --- Montos con IVA desglosado ---
            document.add(new Paragraph("Valores (" + data.currency() + ")", sectionFont));
            PdfPTable amounts = twoColumnTable();
            addRow(amounts, "Subtotal (base)", money(data.baseAmount(), data.currency()));
            addRow(amounts, "IVA (" + percent(data.taxRate()) + ")", money(data.taxAmount(), data.currency()));
            addHighlightedRow(amounts, "Total pagado", money(data.totalAmount(), data.currency()));
            if (data.refundedAmount() != null) {
                addRow(amounts, "Valor reembolsado", money(data.refundedAmount(), data.currency()));
            }
            document.add(amounts);
            document.add(spacer());

            document.add(new Paragraph(
                    "Documento generado el " + format(data.issuedAt())
                            + ". Este comprobante no reemplaza la factura electrónica.", smallFont));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate the PDF receipt", e);
        }

        return out.toByteArray();
    }

    private PdfPTable twoColumnTable() {
        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        return table;
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(LIGHT_GRAY);
        labelCell.setPadding(5);
        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, valueFont));
        valueCell.setPadding(5);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHighlightedRow(PdfPTable table, String label, String value) {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, bold));
        labelCell.setBackgroundColor(LIGHT_GRAY);
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, bold));
        valueCell.setPadding(6);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private Paragraph spacer() {
        return new Paragraph(" ", valueFont);
    }

    private String format(ZonedDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME);
    }

    private String money(BigDecimal amount, String currency) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(new Locale("es", "CO")));
        return "$ " + format.format(amount) + " " + currency;
    }

    private String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}
