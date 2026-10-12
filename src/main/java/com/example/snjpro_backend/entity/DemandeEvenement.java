package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Étape du suivi d'une demande (envoyée, reçue, en traitement, réponse…). */
@Entity
@Table(name = "demande_evenements", indexes = @Index(name = "idx_evenement_demande", columnList = "demande_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DemandeEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false)
    private Demande demande;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(length = 100)
    private String auteur;

    @Column(nullable = false)
    private LocalDateTime date;
}
