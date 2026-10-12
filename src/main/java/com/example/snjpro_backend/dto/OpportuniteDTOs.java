package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.StatutCandidature;
import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import com.example.snjpro_backend.enumeration.Enumerations.TypeOpportunite;
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

public final class OpportuniteDTOs {
    private OpportuniteDTOs() { }

    @Data
    public static class Edition {
        @NotNull
        private TypeOpportunite type = TypeOpportunite.EMPLOI;
        @NotBlank(message = "L'intitulé est obligatoire")
        @Size(max = 150)
        private String intitule;
        @NotBlank(message = "L'organisation est obligatoire")
        @Size(max = 150)
        private String organisation;
        private String lieu;
        private String typeContrat;
        private LocalDate dateLimite;
        private String description;
        private List<String> competences = new ArrayList<>();
        @Schema(description = "COMPETENCES (membres correspondants), TOUS ou REGION")
        private String publicCible = "COMPETENCES";
        private String region;
        private boolean notifierMembres = true;
        private boolean portailPublic;
        @Schema(description = "true : publier immédiatement ; false : enregistrer en brouillon")
        private boolean publier;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private TypeOpportunite type;
        private String intitule;
        private String organisation;
        private String lieu;
        private String typeContrat;
        private LocalDate dateLimite;
        private List<String> competences;
        private StatutOpportunite statut;
        private long nombreCandidatures;
        private long vues;
        private LocalDateTime datePublication;
        @Schema(description = "Pour un membre : correspondance avec son profil (0 à 100)")
        private Integer score;
        private Boolean favori;
        private Boolean candidatureEnvoyee;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail {
        private Resume resume;
        private String description;
        private String publicCible;
        private String region;
        private boolean notifierMembres;
        private boolean portailPublic;
        private Integer membresNotifies;
        private String publiePar;
        private List<String> competencesCorrespondantes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Compteurs {
        private long toutes;
        private long emplois;
        private long stages;
        private long formations;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CandidatureDTO {
        private Long id;
        private Long opportuniteId;
        private String opportunite;
        private String organisation;
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private int score;
        private StatutCandidature statut;
        private String message;
        private LocalDateTime dateCandidature;
    }

    @Data
    public static class Postuler {
        @Size(max = 500)
        private String message;
    }
}
