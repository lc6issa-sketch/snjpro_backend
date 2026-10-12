package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Membre sollicité pour une mobilisation et sa réponse (F25). */
@Entity
@Table(name = "participations_mobilisation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mobilisation_id", "membre_id"}),
        indexes = @Index(name = "idx_participation_membre", columnList = "membre_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParticipationMobilisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mobilisation_id", nullable = false)
    private Mobilisation mobilisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private ReponseMobilisation reponse = ReponseMobilisation.EN_ATTENTE;

    @Column(name = "motif_indisponibilite", length = 255)
    private String motifIndisponibilite;

    @Column(name = "date_reponse")
    private LocalDateTime dateReponse;

    @Column(name = "date_sollicitation", nullable = false)
    private LocalDateTime dateSollicitation;

    @Column(nullable = false)
    private int relances;
}
