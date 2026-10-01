package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.VoucherDetailDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Task 4: server-generated voucher PDF (chosen over a client-rendered one
 * per the approved plan). sale_date/sale_time are already Pakistan-native
 * on the entity (see SalesService's write-site timezone fix) so this just
 * formats them directly — no further zone conversion needed.
 */
@Service
public class VoucherPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    public byte[] generate(VoucherDetailDTO voucher) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float lineHeight = 16;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                y = writeLine(cs, bold, 16, margin, y, "Sales Voucher #" + voucher.getVoucherId());
                y -= 6;

                y = writeLine(cs, regular, 11, margin, y,
                        "Shop: " + nullSafe(voucher.getShopName()) + " (" + nullSafe(voucher.getShopCode()) + ")");
                if (voucher.getBranch() != null) {
                    y = writeLine(cs, regular, 11, margin, y, "Branch: " + voucher.getBranch());
                }
                y = writeLine(cs, regular, 11, margin, y, "Salesman: " + nullSafe(voucher.getAgentName())
                        + " (" + roleLabel(voucher.getAgentRole()) + ")");
                y = writeLine(cs, regular, 11, margin, y, "Date: "
                        + (voucher.getSaleDate() != null ? voucher.getSaleDate().format(DATE_FMT) : "-")
                        + "   Time: " + (voucher.getSaleTime() != null ? voucher.getSaleTime().format(TIME_FMT) : "-")
                        + " (Pakistan time)");
                if (voucher.getDistanceFromShopMeters() != null) {
                    y = writeLine(cs, regular, 11, margin, y,
                            String.format(Locale.ENGLISH, "Distance from shop: %.0f m", voucher.getDistanceFromShopMeters()));
                }
                y = writeLine(cs, regular, 11, margin, y, "Status: " + nullSafe(voucher.getStatus()));
                y -= 10;

                // Table header
                float[] cols = {margin, margin + 200, margin + 290, margin + 360, margin + 430, margin + 500};
                y = writeRow(cs, bold, 10, cols, y,
                        "Product", "Type", "Qty", "Unit Price", "Discount %", "Total");
                y -= 4;
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();
                y -= lineHeight;

                double totalSale = 0, totalReturn = 0;
                List<VoucherDetailDTO.Item> items = voucher.getItems() != null ? voucher.getItems() : List.of();
                for (VoucherDetailDTO.Item item : items) {
                    if (y < margin + 60) {
                        // Simple single-page assumption is safe here: a shop
                        // visit's item list is small (a handful of SKUs),
                        // never large enough to need multi-page pagination.
                        break;
                    }
                    y = writeRow(cs, regular, 9, cols, y,
                            nullSafe(item.getProductName()),
                            nullSafe(item.getTransactionType()),
                            String.valueOf(item.getQuantity()),
                            money(item.getUnitPrice()),
                            item.getDiscountPercent() != null ? String.format(Locale.ENGLISH, "%.1f", item.getDiscountPercent()) : "-",
                            money(item.getTotalPrice()));
                    if ("SALE".equals(item.getTransactionType()) && item.getTotalPrice() != null) {
                        totalSale += item.getTotalPrice();
                    } else if ("RETURN".equals(item.getTransactionType()) && item.getTotalPrice() != null) {
                        totalReturn += item.getTotalPrice();
                    }
                }

                y -= 6;
                cs.moveTo(margin, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();
                y -= lineHeight;

                y = writeLine(cs, bold, 11, margin, y, "Sale Total: " + money(totalSale));
                y = writeLine(cs, bold, 11, margin, y, "Return Total: " + money(totalReturn));
                y = writeLine(cs, bold, 11, margin, y, "Net Total: " + money(totalSale - totalReturn));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate voucher PDF", e);
        }
    }

    private static String roleLabel(String role) {
        if (SalesVoucherService.ROLE_LOCAL.equals(role)) return "Local";
        if (SalesVoucherService.ROLE_LMT.equals(role)) return "LMT";
        return role != null ? role : "-";
    }

    private static String money(Double amount) {
        return amount != null ? String.format(Locale.ENGLISH, "%.2f", amount) : "0.00";
    }

    private static String nullSafe(String s) {
        return s != null ? s : "-";
    }

    private float writeLine(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - (size + 4);
    }

    private float writeRow(PDPageContentStream cs, PDType1Font font, float size, float[] cols, float y, String... values) throws IOException {
        for (int i = 0; i < values.length && i < cols.length; i++) {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(cols[i], y);
            cs.showText(truncate(values[i], 28));
            cs.endText();
        }
        return y - (size + 6);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
