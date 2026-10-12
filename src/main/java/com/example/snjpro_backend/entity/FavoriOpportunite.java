package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Opportunité enregistrée en favori par un membre. */
@Entity
@Table(name = "favoris_opportunite", uniqueConstraints = @UniqueConstraint(columnNames = {"opportunite_id", "membre_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FavoriOpportunite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunite_id", nullable = false)
    private Opportunite opportunite;

    @Column(name = "membre_id", nullable = false)
    private Long membreId;

    @Column(name = "date_ajout", nullable = false)
    private LocalDateTime dateAjout;
}
