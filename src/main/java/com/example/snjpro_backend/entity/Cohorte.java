package com.example.snjpro_backend.entity;



import com.example.snjpro_backend.enumeration.StatutCohorteEnum;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cohortes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cohorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 20)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "annee_debut")
    private Integer anneeDebut;

    @Column(name = "annee_fin")
    private Integer anneeFin;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    @Builder.Default
    private StatutCohorteEnum statut = StatutCohorteEnum.EN_FORMATION;

    /** Numéro d'ordre de la promotion (ex. 22 pour « Cohorte 2021 — N°22 »). */
    private Integer numero;

    /** Centre de formation (ex. Centre de Kati). */
    @Column(length = 100)
    private String centre;

    @Column(name = "recrues_initiales")
    private Integer recruesInitiales;

    @Column(name = "nombre_compagnies")
    private Integer nombreCompagnies;

    @Column(name = "nombre_sections")
    private Integer nombreSections;

    /** Compte du responsable de cohorte (utilisateur ROLE_RESPONSABLE_COHORTE). */
    @Column(name = "responsable_id")
    private Long responsableId;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String responsable;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @OneToMany(mappedBy = "cohorte", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Membre> membres = new ArrayList<>();

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.dateModification = LocalDateTime.now(); }
}
