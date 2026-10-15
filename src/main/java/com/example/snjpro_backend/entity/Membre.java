package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "membres",
        uniqueConstraints = { @UniqueConstraint(columnNames = "identifiant_snj") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Membre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identifiant_snj", nullable = false, unique = true, length = 20)
    private String identifiantSnj;

    @Column(nullable = false, length = 50)
    private String prenom;

    @Column(nullable = false, length = 50)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SexeEnum sexe;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance", length = 100)
    private String lieuNaissance;

    @Column(name = "numero_telephone", length = 20)
    private String numeroTelephone;

    @Column(length = 100)
    private String email;

    @Column(name = "photo_url", length = 255)
    private String photoUrl;

    @Column(name = "telephone_secondaire", length = 20)
    private String telephoneSecondaire;

    /** Numéro vérifié par code SMS (F18). */
    @Column(name = "telephone_verifie")
    private Boolean telephoneVerifie;

    /** Nom de la personne à prévenir. */
    @Column(name = "personne_a_prevenir", length = 150)
    private String personneAPrevenir;

    @Column(name = "personne_a_prevenir_telephone", length = 20)
    private String personneAPrevenirTelephone;

    @Column(length = 50)
    private String nationalite;

    @Column(name = "groupe_sanguin", length = 5)
    private String groupeSanguin;

    /** Taille en mètres. */
    private Double taille;

    /** Poids en kilogrammes. */
    private Double poids;

    /** Diplôme le plus élevé, déduit de la liste des diplômes. */
    @Column(name = "dernier_diplome", length = 50)
    private String dernierDiplome;

    /** Diplômes obtenus, dans l'ordre de saisie. */
    @ElementCollection
    @CollectionTable(name = "membre_diplomes", joinColumns = @JoinColumn(name = "membre_id"))
    @OrderColumn(name = "ordre")
    @Column(name = "diplome", length = 50)
    @Builder.Default
    private List<String> diplomes = new ArrayList<>();

    /** FONCTIONNAIRE ou NON_FONCTIONNAIRE. */
    @Column(length = 30)
    private String categorie;

    @Column(length = 50)
    private String compagnie;

    @Column(length = 50)
    private String section;

    @Column(name = "date_incorporation")
    private LocalDate dateIncorporation;

    /** Le membre accepte d'être sollicité pour les mobilisations. */
    @Column(name = "disponible_mobilisation")
    private Boolean disponibleMobilisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutMembreEnum statut = StatutMembreEnum.ACTIF;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohorte_id", nullable = false)
    private Cohorte cohorte;

    @OneToOne(mappedBy = "membre", cascade = CascadeType.ALL, orphanRemoval = true)
    private SituationProfessionnelle situationProfessionnelle;

    @OneToOne(mappedBy = "membre", cascade = CascadeType.ALL, orphanRemoval = true)
    private Localisation localisation;

    @OneToMany(mappedBy = "membre", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MembreCompetence> competences = new ArrayList<>();

    @OneToMany(mappedBy = "membre", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ParcoursSNJ> parcours = new ArrayList<>();

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.dateModification = LocalDateTime.now(); }
}
