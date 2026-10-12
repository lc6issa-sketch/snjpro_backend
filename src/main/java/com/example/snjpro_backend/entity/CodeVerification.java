package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.TypeCodeVerification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Code à usage unique envoyé par SMS ou e-mail (OTP, lien de réinitialisation, invitation, activation).
 * Le code n'est jamais stocké en clair : seule son empreinte BCrypt est conservée.
 */
@Entity
@Table(name = "codes_verification", indexes = {
        @Index(name = "idx_code_destination", columnList = "type, destination"),
        @Index(name = "idx_code_jeton", columnList = "jeton")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CodeVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private TypeCodeVerification type;

    /** Téléphone ou e-mail auquel le code a été envoyé. */
    @Column(nullable = false, length = 100)
    private String destination;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    /** Jeton opaque remis après vérification du code (étape suivante d'un parcours en plusieurs étapes). */
    @Column(length = 64)
    private String jeton;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "membre_id")
    private Long membreId;

    @Column(nullable = false)
    @Builder.Default
    private int tentatives = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean verifie = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean utilise = false;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
