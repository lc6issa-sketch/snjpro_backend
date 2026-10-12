package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Compétence du référentiel SNJPro (F11). Les compétences des membres ({@link MembreCompetence})
 * y sont rattachées par leur intitulé.
 */
@Entity
@Table(name = "competences_referentiel", uniqueConstraints = @UniqueConstraint(columnNames = "nom"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompetenceReferentiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 100)
    private String categorie;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
