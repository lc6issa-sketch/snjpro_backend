package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutDemande;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDemande;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Demande ou réclamation soumise par un membre (F29). */
@Entity
@Table(name = "demandes", uniqueConstraints = @UniqueConstraint(columnNames = "numero_suivi"),
        indexes = @Index(name = "idx_demande_statut", columnList = "statut"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Demande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro communiqué au membre : DEM-2026-0418. */
    @Column(name = "numero_suivi", nullable = false, length = 20)
    private String numeroSuivi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private TypeDemande type;

    @Column(nullable = false, length = 200)
    private String objet;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_jointe_id")
    private Document pieceJointe;

    /** Modifications demandées (MISE_A_JOUR_PROFIL) au format JSON : [{"champ","ancienne","nouvelle"}]. */
    @Column(columnDefinition = "TEXT")
    private String modifications;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    @Builder.Default
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    @Column(columnDefinition = "TEXT")
    private String reponse;

    @Column(name = "motif_rejet", length = 255)
    private String motifRejet;

    /** Éléments demandés en complément, séparés par des retours à la ligne. */
    @Column(name = "elements_demandes", columnDefinition = "TEXT")
    private String elementsDemandes;

    @Column(name = "date_limite_complement")
    private LocalDate dateLimiteComplement;

    @Column(name = "traite_par", length = 50)
    private String traitePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_prise_en_charge")
    private LocalDateTime datePriseEnCharge;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
