package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Session ouverte (jeton JWT émis) : permet d'afficher les sessions actives et d'en déconnecter une. */
@Entity
@Table(name = "sessions_utilisateurs", indexes = {
        @Index(name = "idx_session_user", columnList = "user_id, active"),
        @Index(name = "idx_session_empreinte", columnList = "empreinte")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SessionUtilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Empreinte SHA-256 du jeton (le jeton n'est jamais stocké). */
    @Column(nullable = false, length = 64)
    private String empreinte;

    /** Navigateur / application déduit du User-Agent (ex. « Chrome · Windows »). */
    @Column(length = 150)
    private String appareil;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Column(nullable = false)
    private boolean active;
}
