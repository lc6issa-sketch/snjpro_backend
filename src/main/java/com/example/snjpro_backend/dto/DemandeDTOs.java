package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.StatutDemande;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDemande;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DemandeDTOs {
    private DemandeDTOs() { }

    /** Modification d'un champ du profil demandée par le membre. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Modification {
        @NotBlank
        @Schema(description = "region, cercle, commune, quartier, adresse, profession, employeur, secteurActivite, email, "
                + "telephone, dateNaissance, lieuNaissance, dernierDiplome, nom, prenom")
        private String champ;
        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        private String ancienne;
        private String nouvelle;
    }

    @Data
    public static class Nouvelle {
        @NotNull
        private TypeDemande type;
        @NotBlank(message = "L'objet est obligatoire")
        @Size(max = 200)
        private String objet;
        @Size(max = 2000)
        private String description;
        @Schema(description = "Pour MISE_A_JOUR_PROFIL : champs à modifier")
        private List<Modification> modifications = new ArrayList<>();
    }

    @Data
    public static class Validation {
        @Size(max = 2000)
        private String reponse;
        @Schema(description = "Applique automatiquement les modifications demandées au profil du membre")
        private boolean appliquerModification = true;
    }

    @Data
    public static class Rejet {
        @NotBlank(message = "Le motif du rejet est obligatoire")
        @Size(max = 255)
        private String motif;
        @Size(max = 2000)
        private String message;
    }

    @Data
    public static class Complement {
        @NotEmpty(message = "Précisez au moins un élément à fournir")
        private List<String> elements = new ArrayList<>();
        @Size(max = 2000)
        private String message;
        @Min(1) @Max(60)
        private int delaiJours = 7;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Evenement {
        private String libelle;
        private String commentaire;
        private String auteur;
        private LocalDateTime date;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String numeroSuivi;
        private TypeDemande type;
        private String objet;
        private StatutDemande statut;
        private Long membreId;
        private String membre;
        private String identifiantSnj;
        private LocalDateTime dateCreation;
        private LocalDateTime dateTraitement;
        private String reponse;
        @Schema(description = "Étape du suivi : 0 envoyée, 1 en traitement, 2 traitée")
        private int etape;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail {
        private Resume resume;
        private String description;
        private String cohorte;
        private String telephone;
        private List<Modification> modifications;
        private Long pieceJointeId;
        private String pieceJointeNom;
        private Long pieceJointeTaille;
        private String motifRejet;
        private List<String> elementsDemandes;
        private LocalDate dateLimiteComplement;
        private String traitePar;
        private String delaiEstime;
        private List<Evenement> suivi;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Statistiques {
        private long enAttente;
        private long enCours;
        private long complementsDemandes;
        private long traiteesCeMois;
        private Double delaiMoyenJours;
    }
}
