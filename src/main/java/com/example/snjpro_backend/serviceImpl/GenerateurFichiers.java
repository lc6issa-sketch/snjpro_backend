package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Production des fichiers téléchargeables : tableaux (Excel, CSV, PDF) et documents PDF simples
 * (attestation, CV numérique).
 */
@Component
public class GenerateurFichiers {
    private static final Color VERT = new Color(0x12, 0x4D, 0x2D);
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    /** Un tableau : titre, en-têtes et lignes de valeurs texte. */
    public record Tableau(String titre, List<String> entetes, List<List<String>> lignes) { }

    /** Un bloc d'un document PDF : un titre de section suivi de lignes libellé / valeur ou de paragraphes. */
    public record Section(String titre, List<String[]> lignes) { }

    public byte[] tableau(Tableau t, FormatFichier format) {
        return switch (format) {
            case XLSX -> excel(t);
            case CSV -> csv(t);
            case PDF -> pdfTableau(t);
        };
    }

    public static String extension(FormatFichier format) { return format.name().toLowerCase(); }

    public static String typeMime(FormatFichier format) {
        return switch (format) {
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case CSV -> "text/csv";
            case PDF -> "application/pdf";
        };
    }

    // ------------------------------------------------------------------ Excel

    private byte[] excel(Tableau t) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(t.titre().length() > 31 ? t.titre().substring(0, 31) : t.titre());
            CellStyle entete = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font gras = wb.createFont();
            gras.setBold(true);
            gras.setColor(IndexedColors.WHITE.getIndex());
            entete.setFont(gras);
            entete.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Row r0 = sheet.createRow(0);
            for (int c = 0; c < t.entetes().size(); c++) {
                org.apache.poi.ss.usermodel.Cell cell = r0.createCell(c);
                cell.setCellValue(t.entetes().get(c));
                cell.setCellStyle(entete);
            }
            for (int i = 0; i < t.lignes().size(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
                List<String> ligne = t.lignes().get(i);
                for (int c = 0; c < ligne.size(); c++) row.createCell(c).setCellValue(ligne.get(c) == null ? "" : ligne.get(c));
            }
            for (int c = 0; c < t.entetes().size(); c++) sheet.autoSizeColumn(c);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ CSV

    private byte[] csv(Tableau t) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM : accents correctement lus par Excel
        sb.append(String.join(";", t.entetes().stream().map(GenerateurFichiers::champCsv).toList())).append('\n');
        for (List<String> ligne : t.lignes())
            sb.append(String.join(";", ligne.stream().map(GenerateurFichiers::champCsv).toList())).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String champCsv(String v) {
        if (v == null) return "";
        return v.contains(";") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    // ------------------------------------------------------------------ PDF

    private byte[] pdfTableau(Tableau t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(t.entetes().size() > 6 ? PageSize.A4.rotate() : PageSize.A4, 30, 30, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        entete(doc, t.titre(), t.lignes().size() + " ligne(s)");
        PdfPTable table = new PdfPTable(Math.max(1, t.entetes().size()));
        table.setWidthPercentage(100);
        Font fEntete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font fCellule = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (String e : t.entetes()) {
            PdfPCell cell = new PdfPCell(new Phrase(e, fEntete));
            cell.setBackgroundColor(VERT);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (List<String> ligne : t.lignes())
            for (String v : ligne) {
                PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, fCellule));
                cell.setPadding(3);
                table.addCell(cell);
            }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    /** Document PDF composé de sections (attestation, CV numérique…). */
    public byte[] document(String titre, String sousTitre, List<Section> sections, String piedDePage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(doc, out);
        doc.open();
        entete(doc, titre, sousTitre);
        Font fSection = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, VERT);
        Font fLibelle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font fValeur = FontFactory.getFont(FontFactory.HELVETICA, 10);
        for (Section s : sections) {
            if (s.titre() != null) {
                Paragraph p = new Paragraph(s.titre(), fSection);
                p.setSpacingBefore(14);
                p.setSpacingAfter(6);
                doc.add(p);
            }
            for (String[] l : s.lignes()) {
                Paragraph p = new Paragraph();
                if (l.length > 1 && l[0] != null) {
                    p.add(new Chunk(l[0] + " : ", fLibelle));
                    p.add(new Chunk(l[1] == null ? "—" : l[1], fValeur));
                } else {
                    p.add(new Chunk(l[l.length - 1] == null ? "" : l[l.length - 1], fValeur));
                }
                p.setSpacingAfter(3);
                doc.add(p);
            }
        }
        if (piedDePage != null) {
            Paragraph p = new Paragraph(piedDePage, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
            p.setSpacingBefore(30);
            doc.add(p);
        }
        doc.close();
        return out.toByteArray();
    }

    private void entete(Document doc, String titre, String sousTitre) {
        doc.add(new Paragraph("SNJPro — Service National des Jeunes du Mali",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, VERT)));
        Paragraph p = new Paragraph(titre, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
        p.setSpacingBefore(6);
        doc.add(p);
        doc.add(new Paragraph((sousTitre == null ? "" : sousTitre + " · ") + "Généré le " + LocalDateTime.now().format(HORODATAGE),
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY)));
        doc.add(new Paragraph(" "));
    }
}
