package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutCandidature;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Candidature d'un membre à une opportunité, avec son score de correspondance. */
@Entity
@Table(name = "candidatures", uniqueConstraints = @UniqueConstraint(columnNames = {"opportunite_id", "membre_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunite_id", nullable = false)
    private Opportunite opportunite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutCandidature statut = StatutCandidature.NOUVELLE;

    /** Correspondance entre les compétences du membre et celles recherchées (0 à 100). */
    @Column(nullable = false)
    private int score;

    @Column(length = 500)
    private String message;

    @Column(name = "date_candidature", nullable = false)
    private LocalDateTime dateCandidature;
}
