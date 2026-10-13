package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.serviceImpl.GenerateurFichiers;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Construction des réponses de téléchargement de fichiers. */
final class Telechargement {
    private Telechargement() { }

    static ResponseEntity<byte[]> fichier(byte[] contenu, String nomFichier, String typeMime, boolean enLigne) {
        ContentDisposition disposition = (enLigne ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nomFichier, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(typeMime))
                .body(contenu);
    }

    static ResponseEntity<byte[]> export(byte[] contenu, String prefixe, FormatFichier format) {
        return fichier(contenu, prefixe + "_" + LocalDate.now() + "." + GenerateurFichiers.extension(format),
                GenerateurFichiers.typeMime(format), false);
    }
}
