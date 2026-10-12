package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Jeton JWT invalidé lors d'une déconnexion. Seule l'empreinte SHA-256 du jeton est stockée.
 */
@Entity
@Table(name = "jetons_revoques", indexes = @Index(name = "idx_jeton_empreinte", columnList = "empreinte", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JetonRevoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String empreinte;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;
}
