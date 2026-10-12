package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.CategorieActualite;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Information ou annonce publiée par le SNJ (F23). */
@Entity
@Table(name = "actualites", indexes = @Index(name = "idx_actualite_statut", columnList = "statut, date_publication"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Actualite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(name = "sous_titre", length = 255)
    private String sousTitre;

    @Column(columnDefinition = "LONGTEXT")
    private String contenu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private CategorieActualite categorie;

    @Column(name = "image_chemin", length = 255)
    private String imageChemin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutActualite statut = StatutActualite.BROUILLON;

    @Column(name = "date_publication")
    private LocalDateTime datePublication;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private CibleNotification cible = CibleNotification.TOUS;

    @Column(name = "cible_valeurs", columnDefinition = "TEXT")
    private String cibleValeurs;

    @Column(name = "notifier_membres", nullable = false)
    private boolean notifierMembres;

    @Column(name = "a_la_une", nullable = false)
    private boolean epingle;

    /** Les membres peuvent s'inscrire (événement, forum…). */
    @Column(name = "inscriptions_ouvertes", nullable = false)
    private boolean inscriptionsOuvertes;

    @Column(nullable = false)
    private long vues;

    @Column(length = 50)
    private String auteur;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.dateModification = LocalDateTime.now(); }
}
