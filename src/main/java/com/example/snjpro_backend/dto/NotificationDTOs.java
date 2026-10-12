package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutNotification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** DTO des notifications (envoi aux membres) et des alertes (panneau web). */
public final class NotificationDTOs {
    private NotificationDTOs() { }

    @Data
    public static class Envoi {
        @NotNull
        private TypeNotification type = TypeNotification.INFORMATION;
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150)
        private String titre;
        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 1000)
        private String message;
        @NotNull
        private CibleNotification cible = CibleNotification.TOUS;
        @Schema(description = "Régions (cible REGION), identifiants de cohortes (COHORTE) ou de membres (SELECTION)")
        private List<String> valeurs = new ArrayList<>();
        private boolean canalApplication = true;
        private boolean canalSms;
        private boolean canalEmail;
        @Schema(description = "Date d'envoi programmé ; vide pour envoyer maintenant")
        private LocalDateTime programmeLe;
        @Schema(description = "true : enregistrer comme brouillon sans envoyer")
        private boolean brouillon;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String titre;
        private String message;
        private TypeNotification type;
        private CibleNotification cible;
        private List<String> valeurs;
        private String destinatairesLibelle;
        private List<String> canaux;
        private StatutNotification statut;
        private LocalDateTime programmeLe;
        private LocalDateTime dateEnvoi;
        private int nombreDestinataires;
        private long nombreLus;
        private int tauxLecture;
        private String emetteur;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Statistiques {
        private long envoyeesCeMois;
        private int tauxLectureMoyen;
        private long smsEnvoyesCeMois;
        private long programmees;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Estimation {
        private int nombreDestinataires;
        private int nombreSms;
    }

    /** Notification vue par un membre (application mobile). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MaNotification {
        private Long id;
        private String titre;
        private String message;
        private TypeNotification type;
        private boolean lu;
        private LocalDateTime date;
        private String lienType;
        private Long lienId;
    }

    /** Alerte du panneau de notifications web. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Alerte {
        private Long id;
        private String titre;
        private String message;
        private String type;
        private String lienType;
        private Long lienId;
        private boolean lu;
        private LocalDateTime date;
    }
}
