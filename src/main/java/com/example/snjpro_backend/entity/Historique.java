package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trace des opérations importantes effectuées dans le système (F34).
 */
@Entity
@Table(name = "historiques", indexes = {
        @Index(name = "idx_historique_entite", columnList = "entite, entite_id"),
        @Index(name = "idx_historique_date", columnList = "date_action")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Historique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 50)
    private String entite;

    @Column(name = "entite_id")
    private Long entiteId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 50)
    private String utilisateur;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    /** Champs modifiés au format JSON : [{"champ":"profession","ancienne":"…","nouvelle":"…"}]. */
    @Column(name = "champs_modifies", columnDefinition = "TEXT")
    private String champsModifies;

    @Column(name = "date_action", nullable = false, updatable = false)
    private LocalDateTime dateAction;

    @PrePersist
    protected void onCreate() { this.dateAction = LocalDateTime.now(); }
}
