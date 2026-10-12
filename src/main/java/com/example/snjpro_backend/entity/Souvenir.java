package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutSouvenir;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Souvenir de formation : publication (photos, vidéos, sons) rattachée à une cohorte.
 * Publié directement par le personnel, ou envoyé par un membre puis modéré (EN_ATTENTE → PUBLIE / REJETE).
 */
@Entity
@Table(name = "souvenirs", indexes = {
        @Index(name = "idx_souvenir_cohorte", columnList = "cohorte_id"),
        @Index(name = "idx_souvenir_statut", columnList = "statut")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Souvenir {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String legende;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohorte_id", nullable = false)
    private Cohorte cohorte;

    /** Date à laquelle les photos / vidéos ont été prises (pendant la formation). */
    @Column(name = "date_prise")
    private LocalDate datePrise;

    @Column(length = 150)
    private String lieu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutSouvenir statut = StatutSouvenir.EN_ATTENTE;

    /** Visible par tous les membres (sinon seulement par les membres de la cohorte). */
    @Column(name = "visible_tous", nullable = false)
    private boolean visibleTous;

    /** Compte qui a publié (username) et nom affiché. */
    @Column(name = "auteur_username", length = 50)
    private String auteurUsername;

    @Column(name = "auteur_nom", length = 120)
    private String auteurNom;

    /** Renseigné quand l'auteur est un membre (envoi depuis l'application mobile). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_membre_id")
    private Membre auteurMembre;

    @Column(name = "motif_rejet", length = 255)
    private String motifRejet;

    @Column(name = "modere_par", length = 50)
    private String moderePar;

    @Column(name = "date_moderation")
    private LocalDateTime dateModeration;

    @OneToMany(mappedBy = "souvenir", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC, id ASC")
    @Builder.Default
    private List<SouvenirMedia> medias = new ArrayList<>();

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
