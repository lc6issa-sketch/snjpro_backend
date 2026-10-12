package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.ModePresence;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Présence d'un membre à une activité. */
@Entity
@Table(name = "presences", uniqueConstraints = @UniqueConstraint(columnNames = {"activite_id", "membre_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Presence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activite_id", nullable = false)
    private Activite activite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Column(name = "date_heure", nullable = false)
    private LocalDateTime dateHeure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private ModePresence mode;

    /** Motif d'une saisie manuelle (téléphone déchargé…). */
    @Column(length = 150)
    private String motif;

    @Column(name = "saisie_par", length = 50)
    private String saisiePar;
}
