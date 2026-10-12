package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import com.example.snjpro_backend.enumeration.SexeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Opération de mobilisation : identification des membres selon des critères, sollicitation
 * et suivi des confirmations de disponibilité (F24, F25, US08).
 */
@Entity
@Table(name = "mobilisations", indexes = @Index(name = "idx_mobilisation_statut", columnList = "statut"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Mobilisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Informations générales (étape 1)
    @Column(nullable = false, length = 150)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Domaine du besoin (Santé & secourisme, BTP…). */
    @Column(name = "type_besoin", length = 100)
    private String typeBesoin;

    @Column(length = 150)
    private String organisateur;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "heure_rendez_vous")
    private LocalTime heureRendezVous;

    @Column(length = 150)
    private String lieu;

    /** Région de l'opération (périmètre du responsable régional). */
    @Column(length = 100)
    private String region;

    @Column(name = "nombre_volontaires")
    private Integer nombreVolontaires;

    @Column(name = "date_limite_reponse")
    private LocalDate dateLimiteReponse;

    /** Prise en charge prévue, séparée par des virgules (Transport, Restauration…). */
    @Column(name = "prise_en_charge", length = 255)
    private String priseEnCharge;

    @Column(nullable = false)
    private boolean urgent;

    // ---- Critères de sélection (étape 2)
    @Column(name = "critere_regions", columnDefinition = "TEXT")
    private String critereRegions;

    @Column(name = "critere_competences", columnDefinition = "TEXT")
    private String critereCompetences;

    @Column(name = "critere_toutes_competences", nullable = false)
    private boolean critereToutesCompetences;

    @Column(name = "critere_cohortes", columnDefinition = "TEXT")
    private String critereCohortes;

    @Column(name = "critere_statuts", length = 255)
    private String critereStatuts;

    @Enumerated(EnumType.STRING)
    @Column(name = "critere_sexe", columnDefinition = "VARCHAR(20)")
    private SexeEnum critereSexe;

    @Column(name = "critere_age_min")
    private Integer critereAgeMin;

    @Column(name = "critere_age_max")
    private Integer critereAgeMax;

    @Column(name = "critere_telephone_verifie", nullable = false)
    private boolean critereTelephoneVerifie;

    @Column(name = "critere_exclure_mobilises", nullable = false)
    private boolean critereExclureMobilises;

    // ---- Envoi (étape 3)
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "canal_application", nullable = false)
    @Builder.Default
    private boolean canalApplication = true;

    @Column(name = "canal_sms", nullable = false)
    private boolean canalSms;

    @Column(name = "canal_email", nullable = false)
    private boolean canalEmail;

    @Column(name = "envoi_programme_le")
    private LocalDateTime envoiProgrammeLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutMobilisation statut = StatutMobilisation.BROUILLON;

    @Column(name = "cree_par", length = 50)
    private String creePar;

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    @Column(name = "date_relance")
    private LocalDateTime dateRelance;

    @Column(name = "date_cloture")
    private LocalDateTime dateCloture;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
