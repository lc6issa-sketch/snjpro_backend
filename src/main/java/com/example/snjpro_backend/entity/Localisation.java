package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "localisations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Localisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Column(length = 100)
    private String pays;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String cercle;

    @Column(length = 100)
    private String commune;

    @Column(length = 100)
    private String quartier;

    @Column(name = "adresse_complete", columnDefinition = "TEXT")
    private String adresseComplete;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @PrePersist
    @PreUpdate
    protected void onUpdate() { this.dateModification = LocalDateTime.now(); }
}
