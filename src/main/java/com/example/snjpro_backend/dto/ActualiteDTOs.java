package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.CategorieActualite;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ActualiteDTOs {
    private ActualiteDTOs() { }

    @Data
    public static class Edition {
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200)
        private String titre;
        @Size(max = 255)
        private String sousTitre;
        private String contenu;
        @NotNull
        private CategorieActualite categorie = CategorieActualite.ANNONCE;
        @Schema(description = "BROUILLON, PROGRAMMEE (avec datePublication future) ou PUBLIEE")
        private StatutActualite statut = StatutActualite.BROUILLON;
        private LocalDateTime datePublication;
        private LocalDate dateExpiration;
        private CibleNotification cible = CibleNotification.TOUS;
        private List<String> valeurs = new ArrayList<>();
        private boolean notifierMembres = true;
        @Schema(description = "Épinglée « À la une »")
        private boolean epingle;
        private boolean inscriptionsOuvertes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String titre;
        private String sousTitre;
        private String extrait;
        private CategorieActualite categorie;
        private StatutActualite statut;
        private String imageUrl;
        private String auteur;
        private LocalDateTime datePublication;
        private LocalDateTime dateModification;
        private long vues;
        @Schema(description = "Épinglée « À la une »")
        private boolean epingle;
        private int minutesLecture;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail {
        private Resume resume;
        private String contenu;
        private LocalDate dateExpiration;
        private CibleNotification cible;
        private List<String> valeurs;
        private boolean notifierMembres;
        private boolean inscriptionsOuvertes;
        private long nombreInscrits;
        private int nombreMots;
        @Schema(description = "Pour un membre : true s'il est inscrit à l'événement")
        private Boolean inscrit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Compteurs {
        private long toutes;
        private long publiees;
        private long brouillons;
        private long programmees;
        private long archivees;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Inscrit {
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private String telephone;
        private LocalDateTime dateInscription;
    }
}
