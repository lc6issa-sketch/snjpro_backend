package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.StatutSouvenir;
import com.example.snjpro_backend.enumeration.Enumerations.TypeMedia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Souvenirs de formation : photos, vidéos et sons partagés par les formés et le personnel. */
public final class SouvenirDTOs {
    private SouvenirDTOs() { }

    /** Champs modifiables d'un souvenir (les fichiers sont envoyés à part, en multipart). */
    @Data
    public static class Edition {
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150)
        private String titre;
        private String legende;
        /** Obligatoire pour le personnel ; ignoré pour un membre (sa propre cohorte). */
        private Long cohorteId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)   // champ de formulaire multipart (yyyy-MM-dd)
        private LocalDate datePrise;
        @Size(max = 150)
        private String lieu;
        private boolean visibleTous;
    }

    @Data
    public static class Rejet {
        @NotBlank(message = "Indiquez le motif du rejet")
        @Size(max = 255)
        private String motif;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Media {
        private Long id;
        private TypeMedia type;
        private String nomFichier;
        private String typeMime;
        private long taille;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String titre;
        private String legende;
        private Long cohorteId;
        private String cohorteNom;
        private LocalDate datePrise;
        private String lieu;
        private StatutSouvenir statut;
        private boolean visibleTous;
        private String auteurNom;
        private Long auteurMembreId;
        private String auteurIdentifiantSnj;
        private boolean envoyeParMembre;
        private String motifRejet;
        private String moderePar;
        private LocalDateTime dateModeration;
        private LocalDateTime dateCreation;
        private List<Media> medias;
        private int nombrePhotos;
        private int nombreVideos;
        private int nombreAudios;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Statistiques {
        private long publies;
        private long enAttente;
        private long rejetes;
        private long photos;
        private long videos;
        private long audios;
    }

    /** Fichier prêt à être renvoyé au client. */
    public record Fichier(byte[] contenu, String nom, String typeMime) { }
}
