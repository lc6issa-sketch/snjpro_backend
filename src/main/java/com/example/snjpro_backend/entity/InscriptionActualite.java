package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Inscription d'un membre à un événement annoncé dans une actualité (forum, journée…). */
@Entity
@Table(name = "inscriptions_actualite", uniqueConstraints = @UniqueConstraint(columnNames = {"actualite_id", "membre_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InscriptionActualite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualite_id", nullable = false)
    private Actualite actualite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Column(name = "date_inscription", nullable = false)
    private LocalDateTime dateInscription;
}
