package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MobilisationDTOs {
    private MobilisationDTOs() { }

    /** Étape 1 : informations générales. */
    @Data
    public static class Informations {
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150)
        private String titre;
        private String description;
        private String typeBesoin;
        private String organisateur;
        @NotNull(message = "La date de début est obligatoire")
        private LocalDate dateDebut;
        private LocalDate dateFin;
        @Schema(type = "string", example = "07:30")
        private LocalTime heureRendezVous;
        private String lieu;
        private String region;
        @Positive
        private Integer nombreVolontaires;
        private LocalDate dateLimiteReponse;
        private List<String> priseEnCharge = new ArrayList<>();
        private boolean urgent;
    }

    /** Étape 2 : critères de sélection des membres. */
    @Data
    public static class Criteres {
        private List<String> regions = new ArrayList<>();
        private List<String> competences = new ArrayList<>();
        @Schema(description = "false : au moins une des compétences ; true : toutes")
        private boolean toutesCompetences;
        private List<Long> cohorteIds = new ArrayList<>();
        private List<StatutMembreEnum> statutsMembre = new ArrayList<>();
        private SexeEnum sexe;
        @Min(15) @Max(99)
        private Integer ageMin;
        @Min(15) @Max(99)
        private Integer ageMax;
        private boolean telephoneVerifieUniquement;
        private boolean exclureDejaMobilises;
    }

    /** Étape 3 : message et envoi. */
    @Data
    public static class Envoi {
        @Size(max = 1000)
        @Schema(description = "Message envoyé ; {prenom} est remplacé par le prénom du membre")
        private String message;
        private boolean canalApplication = true;
        private boolean canalSms = true;
        private boolean canalEmail;
        @Schema(description = "Envoi programmé ; vide pour envoyer maintenant")
        private LocalDateTime programmeLe;
        @Schema(description = "Membres retirés manuellement de la sélection")
        private List<Long> membresExclus = new ArrayList<>();
    }

    @Data
    public static class Cloture {
        private boolean remercierParticipants = true;
        private boolean ajouterAuParcours = true;
    }

    @Data
    public static class ReponseMembre {
        @NotNull
        private Boolean disponible;
        @Size(max = 255)
        private String motif;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Compteurs {
        private long sollicites;
        private long disponibles;
        private long indisponibles;
        private long sansReponse;
        private int tauxReponse;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resume {
        private Long id;
        private String titre;
        private String typeBesoin;
        private String lieu;
        private String region;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private StatutMobilisation statut;
        private boolean urgent;
        private LocalDateTime envoiProgrammeLe;
        private Integer nombreCibles;
        private Compteurs compteurs;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail {
        private Long id;
        private Informations informations;
        private Criteres criteres;
        private Envoi envoi;
        private StatutMobilisation statut;
        private Compteurs compteurs;
        private Integer objectif;
        private int tauxObjectif;
        private LocalDateTime dateEnvoi;
        private LocalDateTime dateRelance;
        private LocalDateTime dateCloture;
        private String creePar;
        /** Réponses reçues par jour depuis l'envoi. */
        private Map<LocalDate, Long> reponsesParJour;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Statistiques {
        private long enCours;
        private long planifiees;
        private long cloturees;
        private long membresSollicitesAnnee;
        private int tauxReponse;
        private Double delaiMoyenReponseHeures;
        private long disponiblesConfirmes;
        private int pourcentageDisponibles;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MembreSelectionne {
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private String region;
        private String cohorte;
        private List<String> competencesCorrespondantes;
        private boolean telephoneVerifie;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Apercu {
        private int total;
        private Map<String, Long> parRegion;
        private int pourcentageFemmes;
        private int pourcentageTelephoneVerifie;
        private int estimationSms;
        private List<MembreSelectionne> membres;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Participant {
        private Long participationId;
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private String region;
        private String competence;
        private ReponseMobilisation reponse;
        private String motif;
        private LocalDateTime dateReponse;
    }

    /** Mobilisation vue par le membre (application mobile). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MaMobilisation {
        private Long id;
        private String titre;
        private String description;
        private String organisateur;
        private String lieu;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private LocalTime heureRendezVous;
        private LocalDate dateLimiteReponse;
        private boolean urgent;
        private StatutMobilisation statut;
        private Integer nombreVolontaires;
        private long placesConfirmees;
        private List<String> profilRecherche;
        private List<String> pourquoiVous;
        private List<String> priseEnCharge;
        private ReponseMobilisation maReponse;
        private LocalDateTime dateReponse;
    }

}
