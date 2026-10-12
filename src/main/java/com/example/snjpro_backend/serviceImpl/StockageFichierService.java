package com.example.snjpro_backend.serviceImpl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stockage des fichiers (documents, photos, pièces jointes, images, rapports) sur le disque du serveur.
 * Les chemins renvoyés sont relatifs au dossier racine {@code app.stockage.dossier}.
 */
@Component
public class StockageFichierService {
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "png", "jpg", "jpeg", "gif", "webp");

    private final Path racine;
    private final long quota;

    public StockageFichierService(@Value("${app.stockage.dossier:uploads}") String dossier,
                                  @Value("${app.stockage.quota-octets:10737418240}") long quota) {
        this.racine = Paths.get(dossier).toAbsolutePath().normalize();
        this.quota = quota;
    }

    /** Enregistre un fichier envoyé et retourne son chemin relatif (ex. documents/3f2a….pdf). */
    public String enregistrer(MultipartFile fichier, String sousDossier) {
        return enregistrer(fichier, sousDossier, EXTENSIONS);
    }

    /** Variante avec une liste d'extensions propre au module (ex. photos, vidéos et sons des souvenirs). */
    public String enregistrer(MultipartFile fichier, String sousDossier, Set<String> extensionsAutorisees) {
        if (fichier == null || fichier.isEmpty())
            throw new IllegalArgumentException("Aucun fichier fourni ou fichier vide.");
        String extension = extension(fichier.getOriginalFilename());
        if (!extensionsAutorisees.contains(extension))
            throw new IllegalArgumentException("Type de fichier non autorisé : ." + extension
                    + " (autorisés : " + String.join(", ", new java.util.TreeSet<>(extensionsAutorisees)) + ")");
        try {
            return ecrire(fichier.getBytes(), sousDossier, extension);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'enregistrer le fichier", e);
        }
    }

    /** Enregistre un contenu généré (PDF, Excel…) et retourne son chemin relatif. */
    public String ecrire(byte[] contenu, String sousDossier, String extension) {
        try {
            Path dossier = racine.resolve(sousDossier).normalize();
            verifierDansRacine(dossier);
            Files.createDirectories(dossier);
            String nom = UUID.randomUUID() + "." + extension;
            Files.write(dossier.resolve(nom), contenu);
            return racine.relativize(dossier.resolve(nom)).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'enregistrer le fichier", e);
        }
    }

    public byte[] lire(String cheminRelatif) {
        if (!StringUtils.hasText(cheminRelatif))
            throw new jakarta.persistence.EntityNotFoundException("Aucun fichier associé.");
        Path chemin = racine.resolve(cheminRelatif).normalize();
        verifierDansRacine(chemin);
        try {
            return Files.readAllBytes(chemin);
        } catch (IOException e) {
            throw new jakarta.persistence.EntityNotFoundException("Fichier introuvable sur le serveur : " + cheminRelatif);
        }
    }

    public void supprimer(String cheminRelatif) {
        if (!StringUtils.hasText(cheminRelatif)) return;
        Path chemin = racine.resolve(cheminRelatif).normalize();
        verifierDansRacine(chemin);
        try {
            Files.deleteIfExists(chemin);
        } catch (IOException ignored) {
            // fichier déjà absent ou verrouillé : sans conséquence pour l'utilisateur
        }
    }

    /** Espace occupé par tous les fichiers stockés, en octets. */
    public long espaceUtilise() {
        if (!Files.exists(racine)) return 0;
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public long quota() { return quota; }

    public static String extension(String nomFichier) {
        if (nomFichier == null || !nomFichier.contains(".")) return "";
        return nomFichier.substring(nomFichier.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static String typeMime(String nomFichier) {
        return switch (extension(nomFichier)) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "3gp" -> "video/3gpp";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg", "oga" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "opus" -> "audio/opus";
            case "csv" -> "text/csv";
            case "txt" -> "text/plain";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private void verifierDansRacine(Path chemin) {
        if (!chemin.startsWith(racine))
            throw new IllegalArgumentException("Chemin de fichier invalide.");
    }
}
