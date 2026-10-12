package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutActivite;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Activité pour laquelle la présence des membres est enregistrée par QR code (F26, US14). */
@Entity
@Table(name = "activites", indexes = @Index(name = "idx_activite_date", columnList = "date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    @Column(name = "heure_fin")
    private LocalTime heureFin;

    @Column(length = 150)
    private String lieu;

    @Column(length = 100)
    private String region;

    /** Mobilisation liée : ses volontaires disponibles constituent la liste des inscrits. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mobilisation_id")
    private Mobilisation mobilisation;

    @Column(name = "qr_actif", nullable = false)
    @Builder.Default
    private boolean qrActif = true;

    /** Secret servant à dériver le code de présence renouvelé toutes les 30 secondes. */
    @Column(name = "secret_qr", nullable = false, length = 64)
    private String secretQr;

    /** Statut forcé (clôture manuelle) ; sinon déduit de la date. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)")
    private StatutActivite statut;

    @Column(name = "cree_par", length = 50)
    private String creePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
