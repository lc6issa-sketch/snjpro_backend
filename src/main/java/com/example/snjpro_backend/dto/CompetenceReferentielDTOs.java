package com.example.snjpro_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class CompetenceReferentielDTOs {
    private CompetenceReferentielDTOs() { }

    @Data
    public static class Edition {
        @NotBlank(message = "Le nom de la compétence est obligatoire")
        @Size(max = 100)
        private String nom;
        @Size(max = 100)
        private String categorie;
        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Competence {
        private Long id;
        private String nom;
        private String categorie;
        private String description;
        private long nombreMembres;
        /** Niveau moyen de 1 (débutant) à 4 (expert) ; null si aucun niveau renseigné. */
        private Double niveauMoyen;
        private Integer anneeAjout;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Categorie {
        private String nom;
        private long nombreCompetences;
        private long nombreMembres;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detenteur {
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private String region;
        private String cohorte;
        private String niveau;
    }
}
