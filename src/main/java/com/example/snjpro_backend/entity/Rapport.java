package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.TypeRapport;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Rapport administratif ou statistique généré (F35). */
@Entity
@Table(name = "rapports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rapport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40)")
    private TypeRapport type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private FormatFichier format;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(length = 100)
    private String region;

    @Column(nullable = false, length = 255)
    private String chemin;

    @Column(nullable = false)
    private long taille;

    @Column(name = "genere_par", length = 50)
    private String generePar;

    @Column(nullable = false)
    private boolean automatique;

    @Column(name = "date_generation", nullable = false, updatable = false)
    private LocalDateTime dateGeneration;

    @PrePersist
    protected void onCreate() { this.dateGeneration = LocalDateTime.now(); }
}
