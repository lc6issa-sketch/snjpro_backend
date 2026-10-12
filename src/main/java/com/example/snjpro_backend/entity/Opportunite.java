package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import com.example.snjpro_backend.enumeration.Enumerations.TypeOpportunite;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Offre d'emploi, de stage ou de formation proposée aux membres (F30). */
@Entity
@Table(name = "opportunites", indexes = @Index(name = "idx_opportunite_statut", columnList = "statut, type"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Opportunite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private TypeOpportunite type;

    @Column(nullable = false, length = 150)
    private String intitule;

    @Column(nullable = false, length = 150)
    private String organisation;

    @Column(length = 100)
    private String lieu;

    /** CDI, CDD 12 mois, 3 mois · gratuite… */
    @Column(name = "type_contrat", length = 100)
    private String typeContrat;

    @Column(name = "date_limite")
    private LocalDate dateLimite;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Compétences recherchées, séparées par des virgules. */
    @Column(columnDefinition = "TEXT")
    private String competences;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutOpportunite statut = StatutOpportunite.BROUILLON;

    /** Public visé : COMPETENCES (membres correspondants), TOUS ou REGION. */
    @Column(name = "public_cible", length = 20)
    @Builder.Default
    private String publicCible = "COMPETENCES";

    @Column(length = 100)
    private String region;

    @Column(name = "notifier_membres", nullable = false)
    private boolean notifierMembres;

    @Column(name = "portail_public", nullable = false)
    private boolean portailPublic;

    @Column(name = "membres_notifies")
    private Integer membresNotifies;

    @Column(nullable = false)
    private long vues;

    @Column(name = "publie_par", length = 50)
    private String publiePar;

    @Column(name = "date_publication")
    private LocalDateTime datePublication;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
