package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Alerte affichée dans le panneau de notifications de l'application web
 * (nouvelle demande, doublons détectés, réponses à une mobilisation, invitation acceptée…).
 */
@Entity
@Table(name = "alertes_utilisateurs", indexes = @Index(name = "idx_alerte_user", columnList = "user_id, lu"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlerteUtilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(length = 255)
    private String message;

    /** Catégorie pour l'icône : DOUBLON, DEMANDE, MOBILISATION, INVITATION, RAPPORT, IMPORT… */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "lien_type", length = 30)
    private String lienType;

    @Column(name = "lien_id")
    private Long lienId;

    @Column(nullable = false)
    private boolean lu;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
