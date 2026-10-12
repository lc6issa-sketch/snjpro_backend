package com.example.snjpro_backend.serviceImpl;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lit un fichier Excel (.xlsx / .xls) ou CSV (séparateur ; ou ,) et le restitue sous forme
 * d'en-têtes + lignes de valeurs texte. Les dates Excel sont converties au format ISO (AAAA-MM-JJ).
 */
@Component
public class LecteurFichierTabulaire {

    public record Tableau(List<String> entetes, List<Ligne> lignes) { }

    /** @param numero numéro de la ligne dans le fichier (en-tête = ligne 1) */
    public record Ligne(int numero, List<String> valeurs) { }

    public Tableau lire(MultipartFile fichier) throws IOException {
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (nom.endsWith(".csv") || nom.endsWith(".txt")) return lireCsv(fichier.getBytes());
        if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) return lireExcel(fichier.getInputStream());
        throw new IllegalArgumentException("Format non supporté : utilisez un fichier .xlsx, .xls ou .csv");
    }

    /** Relit un fichier déjà stocké (assistant d'import en plusieurs étapes). */
    public Tableau lire(byte[] contenu, String nomFichier) throws IOException {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        if (nom.endsWith(".csv") || nom.endsWith(".txt")) return lireCsv(contenu);
        if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) return lireExcel(new java.io.ByteArrayInputStream(contenu));
        throw new IllegalArgumentException("Format non supporté : utilisez un fichier .xlsx, .xls ou .csv");
    }

    private Tableau lireExcel(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row entete = sheet.getRow(sheet.getFirstRowNum());
            if (entete == null) return new Tableau(List.of(), List.of());
            List<String> entetes = new ArrayList<>();
            for (int c = 0; c < entete.getLastCellNum(); c++) {
                entetes.add(valeur(entete.getCell(c), formatter, evaluator));
            }
            List<Ligne> lignes = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> valeurs = new ArrayList<>();
                for (int c = 0; c < entetes.size(); c++) {
                    valeurs.add(valeur(row.getCell(c), formatter, evaluator));
                }
                lignes.add(new Ligne(r + 1, valeurs));
            }
            return new Tableau(entetes, lignes);
        } catch (org.apache.poi.EmptyFileException e) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
    }

    private String valeur(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private Tableau lireCsv(byte[] contenu) throws IOException {
        String texte = new String(contenu, StandardCharsets.UTF_8);
        if (texte.startsWith("﻿")) texte = texte.substring(1); // BOM ajouté par Excel
        String premiereLigne = texte.lines().findFirst().orElse("");
        char separateur = compter(premiereLigne, ';') >= compter(premiereLigne, ',') ? ';' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(separateur)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();
        try (CSVParser parser = CSVParser.parse(new StringReader(texte), format)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) return new Tableau(List.of(), List.of());
            List<String> entetes = new ArrayList<>();
            records.get(0).forEach(entetes::add);
            List<Ligne> lignes = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                CSVRecord rec = records.get(i);
                List<String> valeurs = new ArrayList<>();
                for (int c = 0; c < entetes.size(); c++) {
                    valeurs.add(c < rec.size() ? rec.get(c).trim() : "");
                }
                lignes.add(new Ligne((int) rec.getRecordNumber(), valeurs));
            }
            return new Tableau(entetes, lignes);
        }
    }

    private long compter(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }
}
