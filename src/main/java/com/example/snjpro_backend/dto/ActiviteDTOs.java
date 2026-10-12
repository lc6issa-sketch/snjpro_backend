package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.ModePresence;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActivite;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class ActiviteDTOs {
    private ActiviteDTOs() { }

    @Data
    public static class Edition {
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150)
        private String titre;
        private String description;
        @NotNull(message = "La date est obligatoire")
        private LocalDate date;
        private LocalDate dateFin;
        @Schema(type = "string", example = "08:00")
        private LocalTime heureDebut;
        @Schema(type = "string", example = "13:00")
        private LocalTime heureFin;
        private String lieu;
        private String region;
        private Long mobilisationId;
        private boolean qrActif = true;
    }

    @Data
    public static class SaisieManuelle {
        @NotNull
        private Long membreId;
        @Schema(type = "string", example = "09:55")
        private LocalTime heureArrivee;
        @Size(max = 150)
        private String motif;
    }

    @Data
    public static class Pointage {
        @Schema(description = "Contenu du QR code scanné (SNJPRO:ACT:{id}:{code})")
        private String qr;
        @Schema(description = "Code à 6 caractères saisi manuellement")
        private String code;
        @Schema(description = "Heure du scan (UTC) pour une présence faite hors connexion et envoyée plus tard ; "
                + "absent = pointage en direct", example = "2026-10-04T09:12:30Z")
        private Instant scanneLe;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Compteurs {
        private Long inscrits;
        private long presents;
        private int tauxPresence;
        private long arriveesDerniereHeure;
        private long absentsExcuses;
        private long saisiesManuelles;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String titre;
        private String description;
        private LocalDate date;
        private LocalDate dateFin;
        private LocalTime heureDebut;
        private LocalTime heureFin;
        private String lieu;
        private String region;
        private StatutActivite statut;
        private Long mobilisationId;
        private String mobilisation;
        private boolean qrActif;
        private Compteurs compteurs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PresenceDTO {
        private Long id;
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private LocalDateTime dateHeure;
        private ModePresence mode;
        private String motif;
        private String saisiePar;
    }

    /** Code de présence courant, renouvelé toutes les 30 secondes. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CodeQr {
        private Long activiteId;
        private String code;
        @Schema(description = "Texte à encoder dans le QR code")
        private String contenuQr;
        private int secondesRestantes;
        private LocalTime valableJusqua;
    }

    /** Données de l'écran de projection plein écran. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Projection {
        private Resume activite;
        private CodeQr codeQr;
        private List<PresenceDTO> dernieresArrivees;
    }

    /** Confirmation affichée au membre après son pointage. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmationPresence {
        private Long activiteId;
        private String activite;
        private String organisateur;
        private String lieu;
        private LocalDateTime arrivee;
        private ModePresence mode;
        private boolean ajouteeAuParcours;
        private boolean dejaEnregistree;
    }
}
